package com.example.licenta.Services;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.Reservation;
import com.example.licenta.Models.User;
import com.example.licenta.Repositories.ReservationRepository;
import com.example.licenta.Repositories.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent; // Added import for SetupIntent
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class StripeWebhookService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private ReservationService reservationService;

    public void handlePaymentIntentSucceeded(Event event) {
        try {
            PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (paymentIntent == null) {
                log.error("Could not deserialize PaymentIntent from webhook event for event ID: {}", event.getId());
                return;
            }

            log.info("Processing payment_intent.succeeded webhook for PaymentIntent: {}", paymentIntent.getId());

            Optional<Reservation> reservationOpt = reservationRepository.findByStripePaymentIntentId(paymentIntent.getId());
            if (!reservationOpt.isPresent()) {
                log.warn("No reservation found for PaymentIntent: {} (Event ID: {})", paymentIntent.getId(), event.getId());
                return;
            }

            Reservation reservation = reservationOpt.get();

            if (reservation.getStatus() == ReservationStatus.PAID) {
                log.info("Reservation {} already in PAID status. Idempotent processing for PaymentIntent: {}", reservation.getId(), paymentIntent.getId());
                // Even if PAID, owner earnings might not have been processed if charge.succeeded comes later
                // or if there was a previous failure. We let charge.succeeded handle earnings.
                return;
            }

            if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
                log.warn("Reservation {} for PaymentIntent {} is not in PENDING_PAYMENT status, current status: {}. Skipping update to PAID.", reservation.getId(), paymentIntent.getId(), reservation.getStatus());
                return;
            }

            reservation.setStatus(ReservationStatus.PAID);
            log.info("Updated reservation {} status to PAID via PaymentIntent webhook for PaymentIntent: {}", reservation.getId(), paymentIntent.getId());

            handleLoyaltyPoints(reservation, paymentIntent);
            handlePaymentMethodSaving(reservation, paymentIntent);

            reservationRepository.save(reservation);
            sendPaymentConfirmationEmail(reservation);

            log.info("Successfully processed payment_intent.succeeded for reservation: {} (PaymentIntent: {})", reservation.getId(), paymentIntent.getId());

        } catch (Exception e) {
            log.error("Error processing payment_intent.succeeded webhook for event ID: " + event.getId(), e);
        }
    }

    // Existing Enhanced SetupIntent succeeded handler
    public void handleSetupIntentSucceeded(Event event) {
        try {
            SetupIntent setupIntent = (SetupIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (setupIntent == null) {
                log.error("Could not deserialize SetupIntent from webhook event for event ID: {}", event.getId());
                return;
            }

            log.info("Processing setup_intent.succeeded webhook for SetupIntent: {}", setupIntent.getId());

            Optional<Reservation> reservationOpt = reservationRepository.findByStripeSetupIntentId(setupIntent.getId());
            if (!reservationOpt.isPresent()) {
                log.warn("No reservation found for SetupIntent: {} (Event ID: {})", setupIntent.getId(), event.getId());
                return;
            }

            Reservation reservation = reservationOpt.get();

            if (reservation.getStatus() == ReservationStatus.ACTIVE) {
                log.info("Reservation {} for SetupIntent {} is already ACTIVE. Idempotent processing.", reservation.getId(), setupIntent.getId());
                return;
            }

            if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
                log.warn("Reservation {} for SetupIntent {} is not in PENDING_PAYMENT status, current status: {}. Skipping update to ACTIVE.", reservation.getId(), setupIntent.getId(), reservation.getStatus());
                return;
            }

            String paymentMethodId = setupIntent.getPaymentMethod();
            if (paymentMethodId == null) {
                log.error("SetupIntent {} succeeded but no PaymentMethod ID found. Setting reservation {} to PAYMENT_FAILED.", setupIntent.getId(), reservation.getId());
                reservation.setStatus(ReservationStatus.PAYMENT_FAILED); // Or a more specific setup failure status
                reservationRepository.save(reservation);
                return;
            }

            // The field name in Reservation model seems to be 'savedPaymentMethodId' based on your ReservationService code
            reservation.setSavedPaymentMethodId(paymentMethodId);
            // Also update stripePaymentMethodId if you have it and intend to use it consistently
            // reservation.setStripePaymentMethodId(paymentMethodId);
            reservation.setStatus(ReservationStatus.ACTIVE);
            reservation.setStripeClientSecret(null); // Clear client secret

            log.info("Updated reservation {} status to ACTIVE via SetupIntent webhook for SetupIntent: {}", reservation.getId(), setupIntent.getId());


            if (reservation.getStripeCustomerId() != null) {
                try {
                    stripeService.setDefaultPaymentMethodForCustomer(reservation.getStripeCustomerId(), paymentMethodId);
                    log.info("Set payment method {} as default for customer {} for reservation {}", paymentMethodId, reservation.getStripeCustomerId(), reservation.getId());
                } catch (Exception e) {
                    log.error("Failed to set default payment method {} for customer {} (reservation {}): {}", paymentMethodId, reservation.getStripeCustomerId(), reservation.getId(), e.getMessage());
                }
            } else {
                log.warn("Cannot set default payment method for reservation {} as StripeCustomerId is null.", reservation.getId());
            }

            reservationRepository.save(reservation);
            sendPayForUsageActivationEmail(reservation);

            log.info("Successfully processed setup_intent.succeeded for reservation: {} (SetupIntent: {})", reservation.getId(), setupIntent.getId());

        } catch (Exception e) {
            log.error("Error processing setup_intent.succeeded webhook for event ID: " + event.getId(), e);
        }
    }

    /**
     * Handles the 'charge.succeeded' Stripe webhook event.
     * This event is crucial for confirming the final state of a charge and processing
     * aspects like owner earnings, especially after a PaymentIntent has succeeded.
     */
    public void handleChargeSucceeded(Event event) {
        try {
            Charge charge = (Charge) event.getDataObjectDeserializer().getObject().orElse(null);
            if (charge == null) {
                log.error("Could not deserialize Charge from webhook event for event ID: {}", event.getId());
                return;
            }

            log.info("Processing charge.succeeded webhook for Charge: {}, PaymentIntent: {}", charge.getId(), charge.getPaymentIntent());

            String paymentIntentId = charge.getPaymentIntent();
            if (paymentIntentId == null) {
                log.warn("Charge {} has no associated PaymentIntent. Cannot process owner earnings. (Event ID: {})", charge.getId(), event.getId());
                return;
            }

            Optional<Reservation> reservationOpt = reservationRepository.findByStripePaymentIntentId(paymentIntentId);
            if (!reservationOpt.isPresent()) {
                log.warn("No reservation found for PaymentIntent: {} (associated with Charge: {}). Cannot process owner earnings. (Event ID: {})", paymentIntentId, charge.getId(), event.getId());
                return;
            }

            Reservation reservation = reservationOpt.get();

            // Ensure the reservation is actually paid before processing earnings.
            // payment_intent.succeeded should have set this.
            if (reservation.getStatus() != ReservationStatus.PAID) {
                log.warn("Reservation {} (PaymentIntent: {}) is not in PAID status (current: {}). Skipping owner earnings update for Charge: {}.",
                        reservation.getId(), paymentIntentId, reservation.getStatus(), charge.getId());
                return;
            }

            // Process owner earnings
            updateOwnerEarnings(reservation, charge);

        } catch (Exception e) {
            log.error("Error processing charge.succeeded webhook for event ID: " + event.getId(), e);
        }
    }


    /**
     * Handles the 'setup_intent.setup_failed' Stripe webhook event.
     * This is important for reacting to failures during the card setup process,
     * typically for Pay For Usage reservations.
     */
    public void handleSetupIntentSetupFailed(Event event) {
        try {
            SetupIntent setupIntent = (SetupIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (setupIntent == null) {
                log.error("Could not deserialize SetupIntent from webhook event for event ID: {}", event.getId());
                return;
            }

            log.info("Processing setup_intent.setup_failed webhook for SetupIntent: {}", setupIntent.getId());

            Optional<Reservation> reservationOpt = reservationRepository.findByStripeSetupIntentId(setupIntent.getId());
            if (!reservationOpt.isPresent()) {
                log.warn("No reservation found for SetupIntent: {} (Event ID: {})", setupIntent.getId(), event.getId());
                return;
            }

            Reservation reservation = reservationOpt.get();

            // Only update if it was pending setup. Avoid overriding other terminal states.
            if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
                reservation.setStatus(ReservationStatus.PAYMENT_FAILED); // Or a more specific status like SETUP_FAILED
                reservation.setStripeClientSecret(null); // Clear any client secret
                reservationRepository.save(reservation);
                log.info("Updated reservation {} status to PAYMENT_FAILED due to SetupIntent {} failure. Last Setup Error: {}",
                        reservation.getId(), setupIntent.getId(), setupIntent.getLastSetupError() != null ? setupIntent.getLastSetupError().getMessage() : "N/A");

                // Optionally, send a notification to the user about the setup failure
                // emailService.sendSetupFailedEmail(reservation.getGuestEmail() or user.getEmail(), reservation.getId());

            } else {
                log.warn("Reservation {} for SetupIntent {} was not in PENDING_PAYMENT status (current: {}). No status change made for setup failure.",
                        reservation.getId(), setupIntent.getId(), reservation.getStatus());
            }

        } catch (Exception e) {
            log.error("Error processing setup_intent.setup_failed webhook for event ID: " + event.getId(), e);
        }
    }


    // Existing method to handle payment failures
    public void handlePaymentIntentPaymentFailed(Event event) {
        try {
            PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (paymentIntent == null) {
                log.error("Could not deserialize PaymentIntent from webhook event for event ID: {}", event.getId());
                return;
            }

            log.info("Processing payment_intent.payment_failed webhook for PaymentIntent: {}", paymentIntent.getId());

            Optional<Reservation> reservationOpt = reservationRepository.findByStripePaymentIntentId(paymentIntent.getId());
            if (!reservationOpt.isPresent()) {
                log.warn("No reservation found for PaymentIntent: {} (Event ID: {})", paymentIntent.getId(), event.getId());
                return;
            }

            Reservation reservation = reservationOpt.get();

            if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
                reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                reservation.setStripeClientSecret(null); // Clear any client secret
                reservationRepository.save(reservation);
                log.info("Updated reservation {} status to PAYMENT_FAILED via PaymentIntent webhook. Last Payment Error: {}",
                        reservation.getId(), paymentIntent.getLastPaymentError() != null ? paymentIntent.getLastPaymentError().getMessage() : "N/A");

                // Optionally, send a notification to the user about the payment failure
                // emailService.sendPaymentFailedEmail(reservation.getGuestEmail() or user.getEmail(), reservation.getId());
            } else {
                log.warn("Reservation {} for PaymentIntent {} was not in PENDING_PAYMENT status (current: {}). No status change made for payment failure.",
                        reservation.getId(), paymentIntent.getId(), reservation.getStatus());
            }

        } catch (Exception e) {
            log.error("Error processing payment_intent.payment_failed webhook for event ID: " + event.getId(), e);
        }
    }

    // Helper method to update owner earnings
    private void updateOwnerEarnings(Reservation reservation, Charge charge) {
        ParkingLot parkingLot = reservation.getParkingLot();
        if (parkingLot == null || parkingLot.getOwner() == null) {
            log.warn("Reservation {} (Charge: {}) has no parking lot or owner. Cannot update earnings.", reservation.getId(), charge.getId());
            return;
        }

        User owner = parkingLot.getOwner();

        // Check if earnings already processed for this reservation to ensure idempotency
        // Assuming Reservation model has a boolean field like 'ownerEarningsProcessed'
        if (reservation.getOwnerEarningsProcessed() != null && reservation.getOwnerEarningsProcessed()) {
            log.info("Owner earnings already processed for reservation {} (Charge: {}). Skipping.", reservation.getId(), charge.getId());
            return;
        }

        Double finalAmountPaidByCustomer = reservation.getFinalAmount(); // Amount customer paid after discounts/points
        if (finalAmountPaidByCustomer == null || finalAmountPaidByCustomer <= 0) {
            log.info("Reservation {} (Charge: {}) has zero or negative final amount ({}). No earnings to process for owner.",
                    reservation.getId(), charge.getId(), finalAmountPaidByCustomer);
            // Mark as processed even if zero, to prevent reprocessing
            reservation.setOwnerEarningsProcessed(true);
            reservationRepository.save(reservation);
            return;
        }

        Double netAmountForOwner = calculateNetAmountForOwner(charge, finalAmountPaidByCustomer);

        if (netAmountForOwner <= 0) {
            log.info("Calculated net amount for owner for reservation {} (Charge: {}) is zero or negative ({}). No earnings to add.",
                    reservation.getId(), charge.getId(), netAmountForOwner);
            reservation.setOwnerEarningsProcessed(true);
            reservationRepository.save(reservation);
            return;
        }

        // Update owner's earnings
        Double currentPendingEarnings = Optional.ofNullable(owner.getPendingEarnings()).orElse(0.0);
        Double currentTotalEarnings = Optional.ofNullable(owner.getTotalEarnings()).orElse(0.0);

        owner.setPendingEarnings(BigDecimal.valueOf(currentPendingEarnings)
                .add(BigDecimal.valueOf(netAmountForOwner))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue());
        owner.setTotalEarnings(BigDecimal.valueOf(currentTotalEarnings)
                .add(BigDecimal.valueOf(netAmountForOwner))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue());

        userRepository.save(owner);

        // Mark earnings as processed on the reservation
        reservation.setOwnerEarningsProcessed(true);
        reservationRepository.save(reservation);

        log.info("Successfully updated owner {} earnings for reservation {} (Charge: {}). Added: {}, New pending: {}, New total: {}",
                owner.getUsername(), reservation.getId(), charge.getId(), netAmountForOwner, owner.getPendingEarnings(), owner.getTotalEarnings());
    }

    // Helper method to calculate net amount for owner from a charge
    private Double calculateNetAmountForOwner(Charge charge, Double finalAmountPaidByCustomer) {
        if (charge.getBalanceTransaction() != null) {
            try {
                BalanceTransaction balanceTransaction = BalanceTransaction.retrieve(charge.getBalanceTransaction());
                // Net amount is in the smallest currency unit (e.g., bani for RON)
                double netFromStripe = balanceTransaction.getNet() / 100.0;
                log.info("Net amount from Stripe BalanceTransaction {} for Charge {}: {}", balanceTransaction.getId(), charge.getId(), netFromStripe);
                return BigDecimal.valueOf(netFromStripe).setScale(2, RoundingMode.HALF_UP).doubleValue();
            } catch (StripeException e) {
                log.error("Failed to retrieve BalanceTransaction {} for Charge {}: {}. Falling back to estimate.",
                        charge.getBalanceTransaction(), charge.getId(), e.getMessage());
                // Fallback: Apply a generic estimated platform fee if BalanceTransaction fails.
                // This is a rough estimate and should be adjusted based on actual fee structure.
                // Consider a configurable fee percentage.
                return BigDecimal.valueOf(finalAmountPaidByCustomer * 0.95).setScale(2, RoundingMode.HALF_UP).doubleValue(); // Example: 5% fee
            }
        } else {
            log.warn("Charge {} does not have a BalanceTransaction ID. Falling back to estimate net amount for owner.", charge.getId());
            return BigDecimal.valueOf(finalAmountPaidByCustomer * 0.95).setScale(2, RoundingMode.HALF_UP).doubleValue(); // Example: 5% fee
        }
    }


    // Existing helper methods
    private void handleLoyaltyPoints(Reservation reservation, PaymentIntent paymentIntent) {
        User user = reservation.getUser();
        if (user == null) return;

        Double pointsUsed = reservation.getPointsUsed() != null ? reservation.getPointsUsed() : 0.0;
        Double finalAmountPaid = reservation.getFinalAmount() != null ? reservation.getFinalAmount() : 0.0;

        if (pointsUsed > 0) {
            Double currentPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
            user.setLoyaltyPoints(Math.max(0, currentPoints - pointsUsed));
            log.info("Deducted {} loyalty points for user {} (Reservation {})", pointsUsed, user.getId(), reservation.getId());
        }

        if (finalAmountPaid > 0) {
            // Example: 5 points per 1 unit of currency spent (adjust as needed)
            // If 1 point = 0.1 currency unit, then 1 currency unit = 10 points.
            // If points are awarded as 5% of amount paid:
            double pointsToAdd = finalAmountPaid * 0.05; // This was in your ReservationService
            BigDecimal pointsToAddBD = BigDecimal.valueOf(pointsToAdd).setScale(2, RoundingMode.HALF_UP);
            Double currentPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
            user.setLoyaltyPoints(currentPoints + pointsToAddBD.doubleValue());
            log.info("Added {} loyalty points for user {} (Reservation {}) based on final amount {}", pointsToAddBD.doubleValue(), user.getId(), reservation.getId(), finalAmountPaid);
        }
        userRepository.save(user);
    }

    private void handlePaymentMethodSaving(Reservation reservation, PaymentIntent paymentIntent) {
        String paymentMethodId = paymentIntent.getPaymentMethod();
        // Check if setup_future_usage was set on the PaymentIntent
        String setupFutureUsage = paymentIntent.getSetupFutureUsage();

        if (paymentMethodId != null && setupFutureUsage != null && !setupFutureUsage.isEmpty()) {
            if (reservation.getUser() != null && reservation.getStripeCustomerId() != null) {
                try {
                    // The payment method is already attached to the customer by Stripe if setup_future_usage was used.
                    // We just need to set it as default if that's the desired behavior.
                    stripeService.setDefaultPaymentMethodForCustomer(reservation.getStripeCustomerId(), paymentMethodId);
                    log.info("Set payment method {} as default for customer {} (Reservation {}) due to setup_future_usage",
                            paymentMethodId, reservation.getStripeCustomerId(), reservation.getId());

                    // If it's a Pay For Usage reservation and the saved payment method isn't set yet,
                    // or if you want to update it with the latest one used with setup_future_usage.
                    if (reservation.getReservationType() == ReservationType.PAY_FOR_USAGE) {
                        if (reservation.getSavedPaymentMethodId() == null || !reservation.getSavedPaymentMethodId().equals(paymentMethodId)) {
                            reservation.setSavedPaymentMethodId(paymentMethodId);
                            log.info("Updated savedPaymentMethodId to {} for PFU reservation {}", paymentMethodId, reservation.getId());
                            // No need to save reservation here, as the calling method will save it.
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to set default payment method {} for customer {} (Reservation {}): {}",
                            paymentMethodId, reservation.getStripeCustomerId(), reservation.getId(), e.getMessage());
                }
            }
        }
    }

    private void sendPaymentConfirmationEmail(Reservation reservation) {
        User user = reservation.getUser();
        ParkingLot parkingLot = reservation.getParkingLot();
        String recipientEmail = (user != null && user.getEmail() != null) ? user.getEmail() : reservation.getGuestEmail();

        if (recipientEmail != null && !recipientEmail.isEmpty() && parkingLot != null) {
            String guestToken = null;
            if (user == null) { // For guest reservations
                OffsetDateTime tokenExpiry = reservation.getEndTime() != null ?
                        reservation.getEndTime().plusHours(1) : OffsetDateTime.now(ZoneOffset.UTC).plusHours(24);
                // Assuming generateOrUpdateGuestToken is public or accessible within the same package,
                // or ReservationService is injected. It is injected.
                try {
                    guestToken = reservationService.generateOrUpdateGuestToken(reservation, tokenExpiry).getToken();
                } catch (Exception e) {
                    log.error("Failed to generate guest token for reservation {}: {}", reservation.getId(), e.getMessage());
                }
            }

            emailService.sendReservationConfirmationEmail(
                    recipientEmail,
                    reservation.getId(),
                    parkingLot.getName(),
                    reservation.getStartTime(),
                    reservation.getEndTime(),
                    reservation.getFinalAmount(),
                    guestToken
            );
            log.info("Sent payment confirmation email for reservation {} to {}", reservation.getId(), recipientEmail);
        } else {
            log.warn("Could not send payment confirmation email for reservation {}: recipient or parking lot missing.", reservation.getId());
        }
    }

    private void sendPayForUsageActivationEmail(Reservation reservation) {
        User user = reservation.getUser();
        ParkingLot parkingLot = reservation.getParkingLot();
        String recipientEmail = (user != null && user.getEmail() != null) ? user.getEmail() : reservation.getGuestEmail();

        if (recipientEmail != null && !recipientEmail.isEmpty() && parkingLot != null) {
            String guestToken = null;
            if (user == null) { // For guest PFU reservations
                try {
                    // PFU active tokens might not need an expiry or a very long one
                    guestToken = reservationService.generateOrUpdateGuestToken(reservation, null).getToken();
                } catch (Exception e) {
                    log.error("Failed to generate guest token for PFU activation email for reservation {}: {}", reservation.getId(), e.getMessage());
                }
            }

            emailService.sendPayForUsageActiveEmail(
                    recipientEmail,
                    reservation.getId(),
                    parkingLot.getName(),
                    reservation.getStartTime(),
                    guestToken // guestToken can be null if it's a registered user or token generation failed
            );
            log.info("Sent PFU activation email for reservation {} to {}", reservation.getId(), recipientEmail);
        } else {
            log.warn("Could not send PFU activation email for reservation {}: recipient or parking lot missing.", reservation.getId());
        }
    }
}