package com.example.licenta.Services;

import com.example.licenta.Enum.Reservation.ReservationStatus;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class StripeWebhookService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    public void handleChargeSucceeded(Event event) {
        try {
            Charge charge = (Charge) event.getDataObjectDeserializer().getObject().orElse(null);
            if (charge == null) {
                log.error("Could not deserialize charge from webhook event");
                return;
            }

            log.info("Processing charge.succeeded webhook for charge: {}", charge.getId());

            // Find reservation by PaymentIntent ID
            String paymentIntentId = charge.getPaymentIntent();
            if (paymentIntentId == null) {
                log.warn("Charge {} has no associated PaymentIntent", charge.getId());
                return;
            }

            Optional<Reservation> reservationOpt = reservationRepository.findByStripePaymentIntentId(paymentIntentId);
            if (!reservationOpt.isPresent()) {
                log.warn("No reservation found for PaymentIntent: {}", paymentIntentId);
                return;
            }

            Reservation reservation = reservationOpt.get();

            // Only process if reservation is PAID (payment already confirmed)
            if (reservation.getStatus() != ReservationStatus.PAID) {
                log.info("Reservation {} is not in PAID status, skipping earnings update", reservation.getId());
                return;
            }

            updateOwnerEarnings(reservation, charge);

        } catch (Exception e) {
            log.error("Error processing charge.succeeded webhook", e);
        }
    }

    public void handlePaymentIntentSucceeded(Event event) {
        try {
            PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (paymentIntent == null) {
                log.error("Could not deserialize PaymentIntent from webhook event");
                return;
            }

            log.info("Processing payment_intent.succeeded webhook for PaymentIntent: {}", paymentIntent.getId());

            Optional<Reservation> reservationOpt = reservationRepository.findByStripePaymentIntentId(paymentIntent.getId());
            if (!reservationOpt.isPresent()) {
                log.warn("No reservation found for PaymentIntent: {}", paymentIntent.getId());
                return;
            }

            Reservation reservation = reservationOpt.get();

            // Update reservation status to PAID if it's in PENDING_PAYMENT
            if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
                reservation.setStatus(ReservationStatus.PAID);

                // Update user loyalty points if applicable
                User user = reservation.getUser();
                if (user != null) {
                    Double pointsUsed = reservation.getPointsUsed() != null ? reservation.getPointsUsed() : 0.0;
                    Double finalAmountPaid = reservation.getFinalAmount() != null ? reservation.getFinalAmount() : 0.0;

                    // Deduct points used
                    if (pointsUsed > 0) {
                        Double currentLoyaltyPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
                        user.setLoyaltyPoints(Math.max(0, currentLoyaltyPoints - pointsUsed));
                    }

                    // Add points for payment
                    if (finalAmountPaid > 0) {
                        double pointsToAddUnrounded = finalAmountPaid * 0.05;
                        BigDecimal pointsToAddBigDecimal = BigDecimal.valueOf(pointsToAddUnrounded).setScale(2, RoundingMode.HALF_UP);
                        Double pointsToAdd = pointsToAddBigDecimal.doubleValue();
                        Double currentLoyaltyPointsAfterDeduction = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
                        user.setLoyaltyPoints(currentLoyaltyPointsAfterDeduction + pointsToAdd);
                    }

                    userRepository.save(user);
                }

                reservationRepository.save(reservation);
                log.info("Updated reservation {} status to PAID via PaymentIntent webhook", reservation.getId());
            }

            // Owner earnings will be handled by charge.succeeded webhook
            log.info("PaymentIntent succeeded processing completed for reservation: {}", reservation.getId());

        } catch (Exception e) {
            log.error("Error processing payment_intent.succeeded webhook", e);
        }
    }

    public void handlePaymentIntentPaymentFailed(Event event) {
        try {
            PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (paymentIntent == null) {
                log.error("Could not deserialize PaymentIntent from webhook event");
                return;
            }

            log.info("Processing payment_intent.payment_failed webhook for PaymentIntent: {}", paymentIntent.getId());

            Optional<Reservation> reservationOpt = reservationRepository.findByStripePaymentIntentId(paymentIntent.getId());
            if (!reservationOpt.isPresent()) {
                log.warn("No reservation found for PaymentIntent: {}", paymentIntent.getId());
                return;
            }

            Reservation reservation = reservationOpt.get();

            // Update reservation status to PAYMENT_FAILED
            if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
                reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                reservationRepository.save(reservation);
                log.info("Updated reservation {} status to PAYMENT_FAILED via PaymentIntent webhook", reservation.getId());
            }

        } catch (Exception e) {
            log.error("Error processing payment_intent.payment_failed webhook", e);
        }
    }

    private void updateOwnerEarnings(Reservation reservation, Charge charge) {
        try {
            ParkingLot parkingLot = reservation.getParkingLot();
            if (parkingLot == null || parkingLot.getOwner() == null) {
                log.warn("Reservation {} has no parking lot or owner", reservation.getId());
                return;
            }

            User owner = parkingLot.getOwner();
            Double finalAmountPaid = reservation.getFinalAmount() != null ? reservation.getFinalAmount() : 0.0;

            if (finalAmountPaid <= 0) {
                log.info("Reservation {} has no final amount to process", reservation.getId());
                return;
            }

            // Check if earnings already updated to prevent duplicate processing
            if (reservation.getOwnerEarningsProcessed() != null && reservation.getOwnerEarningsProcessed()) {
                log.info("Owner earnings already processed for reservation {}", reservation.getId());
                return;
            }

            Double netAmountForOwner = calculateNetAmount(charge, finalAmountPaid);

            if (netAmountForOwner <= 0) {
                log.warn("Net amount for owner is <= 0: {}", netAmountForOwner);
                return;
            }

            // Update owner earnings
            updateOwnerEarningsAmounts(owner, netAmountForOwner);

            // Mark as processed
            reservation.setOwnerEarningsProcessed(true);
            reservationRepository.save(reservation);

            log.info("Updated owner {} earnings. Added: {}, New pending: {}, New total: {}",
                    owner.getUsername(), netAmountForOwner, owner.getPendingEarnings(), owner.getTotalEarnings());

        } catch (Exception e) {
            log.error("Error updating owner earnings for reservation {}", reservation.getId(), e);
        }
    }

    private Double calculateNetAmount(Charge charge, Double finalAmountPaid) {
        Double netAmountForOwner = 0.0;

        // Get the net amount from the charge's balance transaction
        if (charge.getBalanceTransaction() != null) {
            try {
                BalanceTransaction balanceTransaction = BalanceTransaction.retrieve(charge.getBalanceTransaction());
                netAmountForOwner = balanceTransaction.getNet() / 100.0; // Convert from cents
                log.info("Retrieved net amount from balance transaction: {}", netAmountForOwner);
            } catch (StripeException e) {
                log.error("Failed to retrieve balance transaction for charge {}", charge.getId(), e);
                // Fallback calculation
                netAmountForOwner = finalAmountPaid * 0.97; // Assume 3% fee
                log.info("Using fallback net amount calculation: {}", netAmountForOwner);
            }
        } else {
            log.warn("No balance transaction found for charge {}, using fallback", charge.getId());
            netAmountForOwner = finalAmountPaid * 0.97;
        }

        return netAmountForOwner;
    }

    private void updateOwnerEarningsAmounts(User owner, Double netAmountForOwner) {
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
    }
}