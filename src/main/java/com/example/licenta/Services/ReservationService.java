package com.example.licenta.Services;

import com.example.licenta.DTOs.*;
import com.example.licenta.Enum.ParkingLot.PricingType;
import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Exceptions.PaymentProcessingException;
import com.example.licenta.Exceptions.ResourceAlreadyExistsException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Mappers.ReservationMapper;
import com.example.licenta.Models.*;
import com.example.licenta.Repositories.*;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.math.RoundingMode;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final UserRepository userRepository;
    private final ReservationMapper reservationMapper;
    private final OpenAiChatModel openAiChatModel;
    private final EmailService emailService;
    private static final Random random = new Random();
    private final GuestAccessTokenRepository guestAccessTokenRepository;
    private final ReviewRepository reviewRepository;
    private final StripeService stripeService;

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
                              ParkingLotRepository parkingLotRepository,
                              UserRepository userRepository,
                              ReservationMapper reservationMapper,
                              OpenAiChatModel openAiChatModel,
                              EmailService emailService,
                              ReviewRepository reviewRepository,
                              GuestAccessTokenRepository guestAccessTokenRepository,
                              StripeService stripeService) {
        this.reservationRepository = reservationRepository;
        this.parkingLotRepository = parkingLotRepository;
        this.userRepository = userRepository;
        this.reservationMapper = reservationMapper;
        this.openAiChatModel = openAiChatModel;
        this.emailService = emailService;
        this.reviewRepository = reviewRepository;
        this.guestAccessTokenRepository = guestAccessTokenRepository;
        this.stripeService = stripeService;
    }

    @Transactional
    public ReservationDTO createDirectReservation(CreateReservationDTO dto) {
        ParkingLot parkingLot = parkingLotRepository.findById(dto.getParkingLotId())
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + dto.getParkingLotId()));

        User user = null;
        if (dto.getUserId() != null) {
            user = userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + dto.getUserId()));
        }

        if (!parkingLot.isAllowDirectPayment()) {
            throw new InvalidDataException("Direct payment is not allowed for this parking lot.");
        }

        OffsetDateTime startTime;
        OffsetDateTime endTime = null;
        try {
            startTime = OffsetDateTime.parse(dto.getStartTime());

            if (dto.getEndTime() != null) {
                endTime = OffsetDateTime.parse(dto.getEndTime());
            }

        } catch (DateTimeParseException e) {
            System.err.println("DateTimeParseException during reservation creation: " + e.getMessage());
            throw new InvalidDataException("Invalid date format. Please use ISO 8601 format.");
        }

        if (dto.getEndTime() != null && (endTime.isBefore(startTime) || endTime.isEqual(startTime))) {
            throw new InvalidDataException("End time must be after start time.");
        }

        Reservation reservation = new Reservation();
        reservation.setParkingLot(parkingLot);
        reservation.setUser(user);
        reservation.setDeviceIdentifier(dto.getDeviceIdentifier());
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setVehiclePlate(dto.getVehiclePlate());
        reservation.setPhoneNumber(dto.getPhoneNumber());
        if (user == null) {
            reservation.setGuestEmail(dto.getGuestEmail());
            reservation.setGuestName(dto.getGuestName());
        }
        reservation.setTotalAmount(dto.getTotalAmount());
        reservation.setPointsUsed(dto.getPointsUsed());
        reservation.setFinalAmount(dto.getFinalAmount());
        reservation.setReservationType(dto.getReservationType());
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);

        Reservation savedReservation = reservationRepository.save(reservation);

        return reservationMapper.toDTO(savedReservation);
    }

    @Transactional(readOnly = true)
    public ReservationDTO getReservationById(String id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
        return reservationMapper.toDTO(reservation);
    }

    @Transactional(readOnly = true)
    public Double calculatePrice(String parkingLotId, OffsetDateTime startTime, OffsetDateTime endTime) {
        if (parkingLotId == null) {
            throw new InvalidDataException("Parking lot ID is required.");
        }
        if (startTime == null || endTime == null) {
            throw new InvalidDataException("Start time and end time are required.");
        }

        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));

        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            throw new InvalidDataException("End time must be after start time.");
        }

        if (parkingLot.getPricingType() == PricingType.DYNAMIC) {
            try {
                String systemMessageContent = "You are a parking price calculator." +
                        "Given the parking lot details and the requested parking duration, " +
                        "predict a fair market price in RON (Romanian Leu). Parking is not free. " +
                        "Respond ONLY with the numerical price value (e.g., 25.50 or 30). Do not provide any other text or explanation. " +
                        "For context, a short stay (1-2 hours) in a central urban lot might be 5-20 RON, while a longer stay in a peripheral lot might be 5-15 RON per few hours. " +
                        "If you cannot determine a price, respond with a typical small non-zero placeholder like 10.00 rather than 0.00.";

                String openAIPrompt = String.format(
                        "Predict the parking price for the following:\n" +
                                "Parking Lot Name: %s\n" +
                                "Parking Lot Address (is in Romania): %s\n" +
                                "Parking Lot Category: %s\n" +
                                "Total Spots: %d\n" +
                                "Available Spots: %d\n" +
                                "Occupancy Rate: %.1f%%\n" +
                                "Requested Start Time: %s\n" +
                                "Requested End Time: %s\n" +
                                "Parking Duration: %d hours\n" +
                                "Current Date and Time (UTC): %s\n" +
                                "Day of Week: %s\n" +
                                "Consider factors like demand, time of day, day of the week, occupancy rate, and parking lot features for a dynamic price.",
                        parkingLot.getName(),
                        parkingLot.getAddress(),
                        parkingLot.getCategory() != null ? parkingLot.getCategory().name() : "N/A",
                        parkingLot.getTotalSpots() != null ? parkingLot.getTotalSpots() : 0,
                        parkingLot.getSpotsAvailable(),
                        calculateOccupancyRate(parkingLot),
                        startTime.toString(),
                        endTime.toString(),
                        Duration.between(startTime, endTime).toMinutes(),
                        OffsetDateTime.now().toString(),
                        OffsetDateTime.now().getDayOfWeek().toString()
                );

                Prompt prompt = new Prompt(List.of(
                        new SystemMessage(systemMessageContent),
                        new UserMessage(openAIPrompt)
                ));

                ChatResponse response = openAiChatModel.call(prompt);
                String predictedPriceStr = null;
                if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                    predictedPriceStr = response.getResult().getOutput().getText().trim();
                }

                if (predictedPriceStr != null && !predictedPriceStr.isEmpty()) {
                    String cleanedPriceStr = predictedPriceStr.replaceAll("[^0-9.]", "");
                    if (cleanedPriceStr.isEmpty() || cleanedPriceStr.equals(".")) {
                        System.err.println("AI model returned an invalid numeric string after cleaning: '" + predictedPriceStr + "'. Falling back to random price.");
                        return generateRandomPrice(parkingLotId, startTime, endTime);
                    }
                    try {
                        BigDecimal predictedPriceBd = new BigDecimal(cleanedPriceStr);
                        predictedPriceBd = predictedPriceBd.setScale(2, RoundingMode.HALF_UP);
                        Double predictedPrice = predictedPriceBd.doubleValue();
                        System.out.println("AI Predicted Price: " + predictedPrice + " for Lot ID: " + parkingLotId + " Start: " + startTime + " End: " + endTime);
                        return predictedPrice;
                    } catch (NumberFormatException e) {
                        System.err.println("Failed to parse AI predicted price: '" + cleanedPriceStr + "'. Error: " + e.getMessage() + ". Falling back to random price.");
                        return generateRandomPrice(parkingLotId, startTime, endTime);
                    }
                } else {
                    System.err.println("AI model did not return a valid price string. Falling back to random price.");
                    return generateRandomPrice(parkingLotId, startTime, endTime);
                }

            } catch (Exception e) {
                System.err.println("Error calling OpenAI model for dynamic pricing: " + e.getMessage());
                return generateRandomPrice(parkingLotId, startTime, endTime);
            }
        } else {
            System.out.println("Pricing type is not DYNAMIC (" + parkingLot.getPricingType() + "). Using fallback price generation.");
            return generateRandomPrice(parkingLotId, startTime, endTime);
        }
    }

    private double calculateOccupancyRate(ParkingLot parkingLot) {
        if (parkingLot.getTotalSpots() == null || parkingLot.getTotalSpots() == 0) return 0.0;
        int occupied = parkingLot.getTotalSpots() - parkingLot.getSpotsAvailable();
        return (occupied * 100.0) / parkingLot.getTotalSpots();
    }

    private Double generateRandomPrice(String parkingLotId, OffsetDateTime startTime, OffsetDateTime endTime) {
        double randomPriceValue = 1.0 + (100.0 * random.nextDouble());
        BigDecimal priceBd = BigDecimal.valueOf(randomPriceValue);
        priceBd = priceBd.setScale(2, RoundingMode.HALF_UP);
        Double price = priceBd.doubleValue();
        System.out.println("Generated Fallback/Mock Price: " + price + " for Lot ID: " + parkingLotId + " Start: " + startTime + " End: " + endTime);
        return price;
    }

    @Transactional(readOnly = true)
    public Page<ReservationDTO> getReservationsByUserId(String userId, List<ReservationType> types, Pageable pageable) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Page<Reservation> reservationPage;
        if (types == null || types.isEmpty()) {
            reservationPage = reservationRepository.findByUserId(userId, pageable);
        } else {
            reservationPage = reservationRepository.findByUserIdAndReservationTypeIn(userId, types, pageable);
        }

        List<ReservationDTO> dtoList = reservationPage.getContent().stream()
                .map(reservationMapper::toDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, reservationPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<ReservationDTO> getReservationsForParkingLotByPeriod(
            String parkingLotId,
            String period,
            Pageable pageable) {

        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot not found: " + parkingLotId));

        Page<Reservation> reservationPage;
        OffsetDateTime now = OffsetDateTime.now();

        if ("ACTIVE".equalsIgnoreCase(period)) {
            reservationPage = reservationRepository.findActiveReservationsForParkingLot(parkingLotId, now, pageable);
        } else if ("UPCOMING".equalsIgnoreCase(period)) {
            reservationPage = reservationRepository.findUpcomingReservationsForParkingLot(parkingLotId, now, pageable);
        } else if ("ENDED".equalsIgnoreCase(period)) {
            reservationPage = reservationRepository.findEndedReservationsForParkingLot(parkingLotId, now, pageable);
        } else if ("ALL".equalsIgnoreCase(period) || !StringUtils.hasText(period)) {
            reservationPage = reservationRepository.findByParkingLotId(parkingLotId, pageable);
        } else {
            reservationPage = reservationRepository.findByParkingLotId(parkingLotId, pageable);
        }

        List<ReservationDTO> dtoList = reservationPage.getContent().stream()
                .map(reservationMapper::toDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, reservationPage.getTotalElements());
    }

    @Transactional
    public ReservationDTO updateReservationStatus(String reservationId, ReservationStatus newStatus) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        reservation.setStatus(newStatus);
        Reservation updatedReservation = reservationRepository.save(reservation);

        return reservationMapper.toDTO(updatedReservation);
    }

    @Transactional(readOnly = true)
    public Optional<ReservationDTO> findActiveReservation(String userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        OffsetDateTime now = OffsetDateTime.now();

        Optional<Reservation> payForUsageActive = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeIsNullAndStatusOrderByStartTimeDesc(
                userId, ReservationType.PAY_FOR_USAGE, now, ReservationStatus.ACTIVE);
        if (payForUsageActive.isPresent()) return Optional.of(reservationMapper.toDTO(payForUsageActive.get()));

        Optional<Reservation> standardActive = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatusOrderByStartTimeDesc(
                userId, ReservationType.STANDARD, now, now, ReservationStatus.PAID);
        if (standardActive.isPresent()) return Optional.of(reservationMapper.toDTO(standardActive.get()));

        Optional<Reservation> directActive = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatusOrderByStartTimeDesc(
                userId, ReservationType.DIRECT, now, now, ReservationStatus.PAID);
        if (directActive.isPresent()) return Optional.of(reservationMapper.toDTO(directActive.get()));

        return Optional.empty();
    }

    @Transactional(readOnly = true) //returns the most upcoming one
    public Optional<ReservationDTO> findUpcomingReservation(String userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        OffsetDateTime now = OffsetDateTime.now();

        List<Reservation> upcomingCandidates = new ArrayList<>();

        // upcoming PAY_FOR_USAGE ACTIVE
        reservationRepository.findFirstByUserIdAndReservationTypeInAndStartTimeAfterAndStatusInOrderByStartTimeAsc(
                        userId,
                        List.of(ReservationType.PAY_FOR_USAGE),
                        now,
                        List.of(ReservationStatus.ACTIVE))
                .ifPresent(upcomingCandidates::add);

        // upcoming STANDARD PAID
        reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeAfterAndStatusOrderByStartTimeAsc(
                        userId, ReservationType.STANDARD, now, ReservationStatus.PAID)
                .ifPresent(upcomingCandidates::add);

        // upcoming DIRECT PAID
        reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeAfterAndStatusOrderByStartTimeAsc(
                        userId, ReservationType.DIRECT, now, ReservationStatus.PAID)
                .ifPresent(upcomingCandidates::add);

        if (upcomingCandidates.isEmpty()) {
            return Optional.empty();
        }

        return upcomingCandidates.stream()
                .min(Comparator.comparing(Reservation::getStartTime))
                .map(reservationMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> findActiveOrUpcomingReservationsForLot(String userId, String parkingLotId, int upcomingWindowHours) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime upcomingWindowEnd = now.plusHours(upcomingWindowHours);

        Set<Reservation> relevantReservations = new HashSet<>();

        // Active PAID Standard reservations
        relevantReservations.addAll(
                reservationRepository.findAllByUserIdAndParkingLotIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatus(
                        userId, parkingLotId, ReservationType.STANDARD, now, now, ReservationStatus.PAID)
        );

        // Active PAID DIRECT reservations
        relevantReservations.addAll(
                reservationRepository.findAllByUserIdAndParkingLotIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatus(
                        userId, parkingLotId, ReservationType.DIRECT, now, now, ReservationStatus.PAID)
        );

        // Active PAY_FOR_USAGE
        relevantReservations.addAll(
                reservationRepository.findAllByUserIdAndParkingLotIdAndReservationTypeAndStartTimeBeforeAndEndTimeIsNullAndStatus(
                        userId, parkingLotId, ReservationType.PAY_FOR_USAGE, now, ReservationStatus.ACTIVE)
        );

        // Upcoming PAID Standard reservations within the window
        relevantReservations.addAll(
                reservationRepository.findAllByUserIdAndParkingLotIdAndReservationTypeAndStartTimeAfterAndStartTimeBeforeAndStatus(
                        userId, parkingLotId, ReservationType.STANDARD, now, upcomingWindowEnd, ReservationStatus.PAID)
        );

        // Upcoming PAID DIRECT reservations within the window
        relevantReservations.addAll(
                reservationRepository.findAllByUserIdAndParkingLotIdAndReservationTypeAndStartTimeAfterAndStartTimeBeforeAndStatus(
                        userId, parkingLotId, ReservationType.DIRECT, now, upcomingWindowEnd, ReservationStatus.PAID)
        );

        // Upcoming PAY_FOR_USAGE reservations with status ACTIVE, within the window
        relevantReservations.addAll(
                reservationRepository.findAllByUserIdAndParkingLotIdAndReservationTypeInAndStartTimeAfterAndStartTimeBeforeAndStatusIn(
                        userId, parkingLotId, List.of(ReservationType.PAY_FOR_USAGE), now, upcomingWindowEnd, List.of(ReservationStatus.ACTIVE))
        );

        return relevantReservations.stream()
                .map(reservationMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    protected GuestAccessToken generateOrUpdateGuestToken(Reservation reservation, OffsetDateTime expiresAt) {
        Optional<GuestAccessToken> existingTokenOpt = guestAccessTokenRepository.findByReservationId(reservation.getId());

        if (existingTokenOpt.isPresent()) {
            GuestAccessToken existingToken = existingTokenOpt.get();
            System.out.println("GuestAccessToken already exists for reservation " + reservation.getId() + ". Updating token and expiry.");
            existingToken.setToken(UUID.randomUUID().toString());
            existingToken.setExpiresAt(expiresAt);
            return guestAccessTokenRepository.save(existingToken);
        } else {
            System.out.println("No GuestAccessToken found for reservation " + reservation.getId() + ". Creating a new one.");
            GuestAccessToken newToken = GuestAccessToken.builder()
                    .token(UUID.randomUUID().toString())
                    .reservation(reservation)
                    .expiresAt(expiresAt)
                    .build();
            return guestAccessTokenRepository.save(newToken);
        }
    }

    @Transactional
    public ReservationDTO endActiveReservation(String reservationId, OffsetDateTime endTime, Double totalAmount) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidDataException("Only active reservations can be ended.");
        }

        reservation.setEndTime(endTime);
        reservation.setTotalAmount(totalAmount);
        reservation.setFinalAmount(totalAmount);
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);

        Reservation updatedReservation = reservationRepository.save(reservation);
        return reservationMapper.toDTO(updatedReservation);
    }

    @Transactional
    public ReservationDTO activatePayForUsageReservation(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getReservationType() != ReservationType.PAY_FOR_USAGE || reservation.getEndTime() != null) {
            throw new InvalidDataException("This operation is only for activating new PAY_FOR_USAGE reservations.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT && reservation.getStatus() != ReservationStatus.PAYMENT_FAILED) {
            throw new InvalidDataException("Reservation is not in a state that allows activation for Pay For Usage. Current status: " + reservation.getStatus());
        }

        User user = reservation.getUser();
        ParkingLot parkingLot = reservation.getParkingLot();

        try {
            String stripeCustomerId = stripeService.getOrCreateStripeCustomerId(user, reservation.getGuestEmail());
            if (user != null && (user.getStripeCustomerId() == null || !user.getStripeCustomerId().equals(stripeCustomerId))) {
                user.setStripeCustomerId(stripeCustomerId);
            }
            reservation.setStripeCustomerId(stripeCustomerId);

            Map<String, String> setupIntentMetadata = Map.of(
                    "reservation_id", reservation.getId(),
                    "user_id", user != null ? user.getId() : "guest-" + reservation.getId(),
                    "parking_lot_id", parkingLot != null ? parkingLot.getId() : "N/A",
                    "intent_purpose", "pay_for_usage_card_setup"
            );

            // Create a SetupIntent instead of a PaymentIntent for card setup
            StripeIntentResponse stripeResponse = stripeService.createSetupIntent(stripeCustomerId, setupIntentMetadata);

            reservation.setStripeSetupIntentId(stripeResponse.getIntentId()); // Store SetupIntent ID
            reservation.setStripePaymentIntentId(null); // Clear any old PaymentIntent ID
            reservation.setStatus(ReservationStatus.PENDING_PAYMENT); // Remains pending until card is setup via PaymentSheet

            Reservation updatedReservation = reservationRepository.save(reservation);
            if (user != null && (user.getStripeCustomerId() == null || !user.getStripeCustomerId().equals(stripeCustomerId) || !Objects.equals(user.getStripeCustomerId(), stripeCustomerId))) {
                userRepository.save(user);
            }

            ReservationDTO dto = reservationMapper.toDTO(updatedReservation);
            dto.setStripeClientSecret(stripeResponse.getClientSecret());
            dto.setStripeOperationType("SETUP_INTENT"); // Indicate the type of intent
            return dto;

        } catch (StripeException e) {
            reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
            reservationRepository.save(reservation);
            throw new PaymentProcessingException("Failed to activate Pay For Usage due to payment setup error: " + e.getMessage());
        }
    }

    @Transactional
    public ReservationDTO savePayForUsagePaymentMethod(String reservationId, String stripePaymentMethodIdFromFrontend) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getReservationType() != ReservationType.PAY_FOR_USAGE) {
            throw new InvalidDataException("This operation is only for PAY_FOR_USAGE reservations.");
        }
        if (reservation.getStripeCustomerId() == null) {
            throw new InvalidDataException("Reservation does not have a Stripe Customer ID.");
        }

        try {
            // This method assumes stripePaymentMethodIdFromFrontend is a valid PM ID already created and confirmed by client.
            // It's typically used if client provides a PM ID directly, not after a SetupIntent flow from PaymentSheet.
            // If this is called *after* a SetupIntent success, the PM is already attached by Stripe.
            // This call might be redundant or for a different flow (e.g., user selects an *already existing* PM from a list).
            stripeService.attachPaymentMethodToCustomer(stripePaymentMethodIdFromFrontend, reservation.getStripeCustomerId());
            stripeService.setDefaultPaymentMethodForCustomer(reservation.getStripeCustomerId(), stripePaymentMethodIdFromFrontend);

            reservation.setStripePaymentMethodId(stripePaymentMethodIdFromFrontend);

            boolean statusChangedToActive = false;
            // If status is PENDING_PAYMENT (meaning client just completed PaymentSheet for SetupIntent,
            // and confirmClientStripePaymentSuccess has run and set the PM), then activate.
            // However, confirmClientStripePaymentSuccess should be the one setting status to ACTIVE.
            // This method might be for a flow where PM is set *without* an immediately preceding SetupIntent flow handled by confirmClientStripePaymentSuccess.
            // For clarity, confirmClientStripePaymentSuccess should handle setting to ACTIVE after SetupIntent.
            if(reservation.getStatus() == ReservationStatus.PENDING_PAYMENT && reservation.getStripeSetupIntentId() == null) {
                // Only activate if not coming from a setup intent flow that confirmClient handles.
                reservation.setStatus(ReservationStatus.ACTIVE);
                statusChangedToActive = true;
            } else if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT && reservation.getStripeSetupIntentId() != null) {
                // This means confirmClientStripePaymentSuccess should have already handled activation.
                // This path in savePayForUsagePaymentMethod might indicate a logic conflict or it's for updating PM later.
                System.out.println("savePayForUsagePaymentMethod called for a reservation with a recent SetupIntent. Status should be handled by confirmClientStripePaymentSuccess.");
            }


            Reservation savedReservation = reservationRepository.save(reservation);

            if (statusChangedToActive) {
                User user = reservation.getUser();
                ParkingLot parkingLot = reservation.getParkingLot();
                String recipientEmail = (user != null && user.getEmail() != null) ? user.getEmail() : reservation.getGuestEmail();
                String guestAccessTokenString = null;
                if (user == null && parkingLot != null) {
                    guestAccessTokenString = generateOrUpdateGuestToken(savedReservation, null).getToken();
                }
                if (recipientEmail != null && !recipientEmail.isEmpty() && parkingLot != null) {
                    emailService.sendPayForUsageActiveEmail(recipientEmail, savedReservation.getId(), parkingLot.getName(), savedReservation.getStartTime(), guestAccessTokenString);
                }
            }
            return reservationMapper.toDTO(savedReservation);
        } catch (StripeException e) {
            throw new PaymentProcessingException("Failed to save payment method with Stripe: " + e.getMessage());
        }
    }

    @Transactional
    public ReservationDTO processStandardOrDirectPayment(String reservationId, PaymentRequestDTO paymentRequest) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getReservationType() == ReservationType.PAY_FOR_USAGE) {
            throw new InvalidDataException("This method is for STANDARD or DIRECT reservations.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT && reservation.getStatus() != ReservationStatus.PAYMENT_FAILED) {
            throw new InvalidDataException("Reservation does not have a valid status for payment initiation. Current status: " + reservation.getStatus());
        }
        if (reservation.getEndTime() == null && reservation.getReservationType() != ReservationType.PAY_FOR_USAGE) {
            throw new InvalidDataException("Reservation of type " + reservation.getReservationType() + " must have an end time for payment processing.");
        }

        User user = reservation.getUser();
        ParkingLot parkingLot = reservation.getParkingLot();
        Double pointsToUse = (paymentRequest != null && paymentRequest.getPointsToUse() != null) ? paymentRequest.getPointsToUse() : 0.0;
        validateUserPoints(user, pointsToUse);

        Double totalAmountForReservation = reservation.getTotalAmount();
        if (totalAmountForReservation == null || totalAmountForReservation < 0) {
            throw new InvalidDataException("Reservation does not have a valid total amount.");
        }
        Double finalAmountCustomerPays = calculateFinalAmount(totalAmountForReservation, pointsToUse);

        if (totalAmountForReservation <= 0) {
            reservation.setStatus(ReservationStatus.PAID);
            reservation.setFinalAmount(0.0);
            reservation.setPointsUsed(0.0);
            Reservation savedReservation = reservationRepository.save(reservation);
            return reservationMapper.toDTO(savedReservation);
        }

        try {
            String stripeCustomerId = stripeService.getOrCreateStripeCustomerId(user, reservation.getGuestEmail());
            if (user != null && (user.getStripeCustomerId() == null || !user.getStripeCustomerId().equals(stripeCustomerId))) {
                user.setStripeCustomerId(stripeCustomerId);
            }
            reservation.setStripeCustomerId(stripeCustomerId);

            long amountToChargeInSmallestUnit = Math.round(finalAmountCustomerPays * 100);
            if (amountToChargeInSmallestUnit < 0) amountToChargeInSmallestUnit = 0;

            Map<String, String> paymentIntentMetadata = createPaymentMetadata(reservation, user, parkingLot);

            String setupFutureUsageValue = (user != null) ? "on_session" : null;
            if (paymentRequest != null && paymentRequest.getSetupFutureUsage() != null && !paymentRequest.getSetupFutureUsage().isEmpty()) {
                setupFutureUsageValue = paymentRequest.getSetupFutureUsage();
            }

            StripeIntentResponse stripeResponse = stripeService.createPaymentIntent(
                    amountToChargeInSmallestUnit,
                    "RON",
                    stripeCustomerId,
                    null,
                    paymentIntentMetadata,
                    false,
                    setupFutureUsageValue
            );

            reservation.setStripePaymentIntentId(stripeResponse.getIntentId());
            reservation.setPointsUsed(pointsToUse);
            reservation.setFinalAmount(finalAmountCustomerPays);
            reservation.setStatus(ReservationStatus.PENDING_PAYMENT);

            Reservation updatedReservation = reservationRepository.save(reservation);
            if (user != null && (user.getStripeCustomerId() == null || !user.getStripeCustomerId().equals(stripeCustomerId) || !Objects.equals(user.getStripeCustomerId(), stripeCustomerId))) {
                userRepository.save(user);
            }
            ReservationDTO dto = reservationMapper.toDTO(updatedReservation);
            dto.setStripeClientSecret(stripeResponse.getClientSecret());
            dto.setStripeOperationType("PAYMENT_INTENT");
            return dto;

        } catch (StripeException e) {
            reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
            reservationRepository.save(reservation);
            throw new PaymentProcessingException("Stripe payment processing failed: " + e.getMessage());
        }
    }

    @Transactional
    public ReservationDTO confirmClientStripePaymentSuccess(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.PAID || reservation.getStatus() == ReservationStatus.ACTIVE) {
            System.out.println("Reservation " + reservationId + " is already PAID or ACTIVE. Idempotent confirmation.");
            return reservationMapper.toDTO(reservation);
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new InvalidDataException("Reservation " + reservationId + " is not in PENDING_PAYMENT state for confirmation. Current status: " + reservation.getStatus());
        }

        User user = reservation.getUser();
        ParkingLot parkingLot = reservation.getParkingLot();

        try {
            // Check if this confirmation is for a SetupIntent (PFU Activation)
            if (reservation.getStripeSetupIntentId() != null) {
                SetupIntent setupIntent = stripeService.retrieveSetupIntent(reservation.getStripeSetupIntentId());

                if ("succeeded".equals(setupIntent.getStatus())) {
                    String paymentMethodId = setupIntent.getPaymentMethod();
                    if (paymentMethodId == null) {
                        throw new PaymentProcessingException("SetupIntent succeeded but no PaymentMethod ID found.");
                    }
                    reservation.setStripePaymentMethodId(paymentMethodId);
                    reservation.setStatus(ReservationStatus.ACTIVE); // PFU is now active with a saved card

                    if (user != null && reservation.getStripeCustomerId() != null) {
                        try {
                            stripeService.setDefaultPaymentMethodForCustomer(reservation.getStripeCustomerId(), paymentMethodId);
                        } catch (StripeException e) {
                            System.err.println("Failed to set default payment method for customer " + reservation.getStripeCustomerId() + " after SetupIntent: " + e.getMessage());
                        }
                    }
                    System.out.println("Pay For Usage card setup successful via SetupIntent for reservation " + reservationId + ". Status set to ACTIVE.");

                    String recipientEmailPfu = (user != null && user.getEmail() != null) ? user.getEmail() : reservation.getGuestEmail();
                    String guestTokenPfu = null;
                    if (user == null && parkingLot != null) {
                        guestTokenPfu = generateOrUpdateGuestToken(reservation, null).getToken();
                    }
                    if (recipientEmailPfu != null && !recipientEmailPfu.isEmpty() && parkingLot != null) {
                        emailService.sendPayForUsageActiveEmail(recipientEmailPfu, reservation.getId(), parkingLot.getName(), reservation.getStartTime(), guestTokenPfu);
                    }
                } else {
                    System.err.println("Stripe SetupIntent " + setupIntent.getId() + " for reservation " + reservationId +
                            " is not 'succeeded'. Actual status: " + setupIntent.getStatus() + ". Setting reservation status to PAYMENT_FAILED.");
                    reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                    reservationRepository.save(reservation);
                    throw new PaymentProcessingException("Stripe card setup verification failed. Status: " + setupIntent.getStatus());
                }
            }
            else if (reservation.getStripePaymentIntentId() != null) {
                PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(reservation.getStripePaymentIntentId());

                if ("succeeded".equals(paymentIntent.getStatus())) {
                    reservation.setStatus(ReservationStatus.PAID);

                    Double pointsUsed = reservation.getPointsUsed() != null ? reservation.getPointsUsed() : 0.0;
                    Double finalAmountPaid = reservation.getFinalAmount() != null ? reservation.getFinalAmount() : 0.0;

                    // Update user loyalty points
                    if (user != null && pointsUsed > 0) {
                        Double currentLoyaltyPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
                        user.setLoyaltyPoints(Math.max(0, currentLoyaltyPoints - pointsUsed));
                    }
                    if (user != null && finalAmountPaid > 0) {
                        double pointsToAddUnrounded = finalAmountPaid * 0.05;
                        BigDecimal pointsToAddBigDecimal = BigDecimal.valueOf(pointsToAddUnrounded).setScale(2, RoundingMode.HALF_UP);
                        Double pointsToAdd = pointsToAddBigDecimal.doubleValue();
                        Double currentLoyaltyPointsAfterDeduction = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
                        user.setLoyaltyPoints(currentLoyaltyPointsAfterDeduction + pointsToAdd);
                    }

                    // REMOVED: Owner earnings logic - now handled by webhook service

                    // Handle payment method saving for future use
                    String paymentMethodIdFromPI = paymentIntent.getPaymentMethod();
                    if (paymentMethodIdFromPI != null && paymentIntent.getSetupFutureUsage() != null && !paymentIntent.getSetupFutureUsage().isEmpty()) {
                        if (user != null && reservation.getStripeCustomerId() != null) {
                            try {
                                stripeService.setDefaultPaymentMethodForCustomer(reservation.getStripeCustomerId(), paymentMethodIdFromPI);
                                if(reservation.getReservationType() == ReservationType.PAY_FOR_USAGE && reservation.getStripePaymentMethodId() == null){
                                    reservation.setStripePaymentMethodId(paymentMethodIdFromPI);
                                }
                            } catch (StripeException e) {
                                System.err.println("Failed to set default payment method for customer " + reservation.getStripeCustomerId() + " after PaymentIntent with setup_future_usage: " + e.getMessage());
                            }
                        }
                    }

                    // Send confirmation email
                    String recipientEmailPaid = (user != null && user.getEmail() != null) ? user.getEmail() : reservation.getGuestEmail();
                    String guestTokenPaid = null;
                    if (user == null && parkingLot != null) {
                        OffsetDateTime tokenExpiry = reservation.getEndTime() != null ? reservation.getEndTime().plusHours(1) : OffsetDateTime.now(ZoneOffset.UTC).plusHours(24);
                        guestTokenPaid = generateOrUpdateGuestToken(reservation, tokenExpiry).getToken();
                    }
                    if (recipientEmailPaid != null && !recipientEmailPaid.isEmpty() && parkingLot != null) {
                        emailService.sendReservationConfirmationEmail(recipientEmailPaid, reservation.getId(), parkingLot.getName(), reservation.getStartTime(), reservation.getEndTime(), finalAmountPaid, guestTokenPaid);
                    }
                } else {
                    System.err.println("Stripe PaymentIntent " + paymentIntent.getId() + " for reservation " + reservationId +
                            " is not 'succeeded'. Actual status: " + paymentIntent.getStatus() + ". Setting reservation status to PAYMENT_FAILED.");
                    reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                    reservationRepository.save(reservation);
                    throw new PaymentProcessingException("Stripe payment verification failed. Status: " + paymentIntent.getStatus());
                }
            } else {
                throw new InvalidDataException("Reservation " + reservationId + " does not have a Stripe Intent ID (Payment or Setup) for verification.");
            }

            if (user != null) {
                userRepository.save(user);
            }
            Reservation updatedReservation = reservationRepository.save(reservation);
            return reservationMapper.toDTO(updatedReservation);

        } catch (StripeException e) {
            System.err.println("Stripe API error during payment confirmation for reservation " + reservationId + ": " + e.getMessage() + ". Setting reservation status to PAYMENT_FAILED.");
            if (reservation.getStatus() != ReservationStatus.PAID && reservation.getStatus() != ReservationStatus.ACTIVE) {
                reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                reservationRepository.save(reservation);
            }
            throw new PaymentProcessingException("Failed to confirm payment/setup with Stripe: " + e.getMessage());
        }
    }

    @Transactional
    public ReservationDTO endActivePayForUsageReservationAndInitiatePayment(String reservationId, OffsetDateTime endTime, Double totalAmount) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getReservationType() != ReservationType.PAY_FOR_USAGE) {
            throw new InvalidDataException("This method is only for ending PAY_FOR_USAGE reservations.");
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidDataException("Only active PAY_FOR_USAGE reservations can be ended. Current status: " + reservation.getStatus());
        }

        User user = reservation.getUser();
        ParkingLot parkingLot = reservation.getParkingLot();
        reservation.setEndTime(endTime);
        reservation.setTotalAmount(totalAmount); // This is the gross amount for the session

        // CRITICAL: For PFU, the payment method should have been saved during activation (SetupIntent flow)
        if (reservation.getStripePaymentMethodId() == null || reservation.getStripeCustomerId() == null) {
            reservation.setFinalAmount(totalAmount); // What they owe
            reservation.setStatus(ReservationStatus.PAYMENT_FAILED); // Cannot charge automatically
            reservationRepository.save(reservation);
            throw new PaymentProcessingException("Payment method not saved for this Pay For Usage session. Cannot process automatic payment.");
        }

        // If total amount is zero or less (e.g. promotions, or error), complete without charging.
        if (totalAmount <= 0) {
            // Using a helper or inline logic to set to PAID, adjust points, send email etc.
            // For PFU, points are typically not used for the session charge itself, but for simplicity here:
            return completeZeroOrNegativePfuPayment(reservation, user, parkingLot, totalAmount);
        }

        try {
            long amountToChargeInSmallestUnit = Math.round(totalAmount * 100);
            Map<String, String> paymentIntentMetadata = createPaymentMetadata(reservation, user, parkingLot);

            // Create a PaymentIntent to charge the saved card
            // IMPORTANT: use reservation.getStripePaymentMethodId() and reservation.getStripeCustomerId()
            // Set offSession to true as this is a merchant-initiated transaction (MIT)
            StripeIntentResponse stripeResponse = stripeService.createPaymentIntent(
                    amountToChargeInSmallestUnit,
                    "RON",
                    reservation.getStripeCustomerId(),
                    reservation.getStripePaymentMethodId(), // Use the saved payment method
                    paymentIntentMetadata,
                    true // offSession = true
            );

            reservation.setStripePaymentIntentId(stripeResponse.getIntentId()); // Store PI ID for this charge attempt
            reservation.setFinalAmount(totalAmount); // What we attempted to charge
            reservation.setPointsUsed(0.0); // PFU typically doesn't use points for session charge this way

            // Status becomes PENDING_PAYMENT while Stripe processes the off-session charge.
            // A webhook (payment_intent.succeeded/failed) should ideally update to PAID/PAYMENT_FAILED.
            // If relying on polling, client calls confirmClientStripePaymentSuccess.
            reservation.setStatus(ReservationStatus.PENDING_PAYMENT);

            Reservation updatedReservation = reservationRepository.save(reservation);
            ReservationDTO dto = reservationMapper.toDTO(updatedReservation);

            // If immediate success (rare for off-session without SCA challenge, but possible)
            if ("succeeded".equals(stripeResponse.getStatus())) {
                // This would ideally be handled by confirmClientStripePaymentSuccess or webhook
                // but for direct response:
                dto = confirmClientStripePaymentSuccess(reservationId); // Re-confirm to finalize points/email
            }
            // If SCA is required for the off-session payment
            else if ("requires_action".equals(stripeResponse.getStatus()) || "requires_confirmation".equals(stripeResponse.getStatus())) {
                dto.setStripeClientSecret(stripeResponse.getClientSecret());
                dto.setStripeOperationType("PAYMENT_INTENT_REQUIRES_ACTION");
            } else if ("processing".equals(stripeResponse.getStatus())){
                dto.setStripeOperationType("PAYMENT_INTENT_PROCESSING");
            }
            // Other statuses like 'requires_payment_method' would indicate an issue with the saved card.
            else if ("requires_payment_method".equals(stripeResponse.getStatus())){
                reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                reservationRepository.save(reservation);
                dto = reservationMapper.toDTO(reservation);
                // Inform user their saved card failed.
            }


            return dto;
        } catch (StripeException e) {
            reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
            reservationRepository.save(reservation);
            throw new PaymentProcessingException("Payment processing failed for Pay For Usage session: " + e.getMessage());
        }
    }

    private ReservationDTO completeZeroOrNegativePfuPayment(Reservation reservation, User user, ParkingLot parkingLot, Double finalAmount) {
        reservation.setStatus(ReservationStatus.PAID); // Or COMPLETED if you have such status
        reservation.setFinalAmount(finalAmount); // Should be 0 or less
        reservation.setPointsUsed(0.0); // Typically no points involved here

        Reservation updatedReservation = reservationRepository.save(reservation);

        // Send confirmation email for the completed PFU session
        String recipientEmail = (user != null && user.getEmail() != null) ? user.getEmail() : reservation.getGuestEmail();
        String guestAccessTokenString = null;
        if (user == null && parkingLot != null) {
            OffsetDateTime tokenExpiry = updatedReservation.getEndTime() != null ? updatedReservation.getEndTime().plusHours(1) : OffsetDateTime.now(ZoneOffset.UTC).plusHours(24);
            guestAccessTokenString = generateOrUpdateGuestToken(updatedReservation, tokenExpiry).getToken();
        }
        if (recipientEmail != null && !recipientEmail.isEmpty() && parkingLot != null) {
            emailService.sendReservationConfirmationEmail( // Or a specific PFU completion email
                    recipientEmail,
                    updatedReservation.getId(),
                    parkingLot.getName(),
                    updatedReservation.getStartTime(),
                    updatedReservation.getEndTime(),
                    finalAmount, // Will be 0.00
                    guestAccessTokenString
            );
        }
        return reservationMapper.toDTO(updatedReservation);
    }

    private Map<String, String> createPaymentMetadata(Reservation reservation, User user, ParkingLot parkingLot) {
        return Map.of("reservation_id", reservation.getId(),"internal_user_id", user != null ? user.getId() : "guest-" + reservation.getId(),"parking_lot_id", parkingLot.getId());
    }

    private Double calculateFinalAmount(Double totalAmount, Double pointsToUse) {
        if (totalAmount == null) return 0.0;
        Double finalAmount = totalAmount;
        if (pointsToUse > 0) {
            finalAmount = totalAmount - (pointsToUse * 0.1);
            if (finalAmount < 0) finalAmount = 0.0;
        }
        return BigDecimal.valueOf(finalAmount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private void validateUserPoints(User user, Double pointsToUse) {
        if (user != null && pointsToUse > 0) {
            Double userCurrentPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
            if (userCurrentPoints < pointsToUse) {
                throw new InvalidDataException("Insufficient loyalty points. Available: " + userCurrentPoints + ", Requested: " + pointsToUse);
            }
        }
    }

    @Transactional
    public ReservationDTO handlePayment(String reservationId, PaymentRequestDTO paymentRequest) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        ParkingLot parkingLot = reservation.getParkingLot();
        if (parkingLot == null) {
            throw new InvalidDataException("Critical error: Reservation (ID: " + reservationId + ") is not associated with a parking lot.");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT && reservation.getStatus() != ReservationStatus.PAYMENT_FAILED) {
            throw new InvalidDataException("Reservation (ID: " + reservationId + ") does not have a valid status for payment. Current status: " + reservation.getStatus());
        }

        User user = reservation.getUser();
        String recipientEmail = (user != null && user.getEmail() != null) ?
                user.getEmail() : reservation.getGuestEmail();
        String guestAccessTokenString = null;

        Double pointsToUse = (paymentRequest != null && paymentRequest.getPointsToUse() != null) ? paymentRequest.getPointsToUse() : 0.0;
        if (user != null && pointsToUse > 0) {
            Double userCurrentPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
            if (userCurrentPoints < pointsToUse) {
                throw new InvalidDataException("Insufficient loyalty points. Available: " + userCurrentPoints + ", Requested: " + pointsToUse);
            }
        }

        if (reservation.getReservationType() == ReservationType.PAY_FOR_USAGE && reservation.getEndTime() == null) {
            // ... (Pay for usage activation logic - remains the same)
            reservation.setStatus(ReservationStatus.ACTIVE);

            if (user == null) {
                GuestAccessToken guestToken = generateOrUpdateGuestToken(reservation, null);
                guestAccessTokenString = guestToken.getToken();
            }

            Reservation updatedReservation = reservationRepository.save(reservation);

            if (recipientEmail != null && !recipientEmail.isEmpty()) {
                emailService.sendPayForUsageActiveEmail(
                        recipientEmail,
                        updatedReservation.getId(),
                        parkingLot.getName(),
                        updatedReservation.getStartTime(),
                        guestAccessTokenString
                );
            }
            return reservationMapper.toDTO(updatedReservation);

        } else if (reservation.getReservationType() != ReservationType.PAY_FOR_USAGE && reservation.getEndTime() == null) {
            // ... (Invalid state logic - remains the same)
            throw new InvalidDataException("Reservation (ID: " + reservationId + ") of type " +
                    reservation.getReservationType() + " must have an end time for final payment processing.");
        } else {
            User owner = parkingLot.getOwner();
            Double totalAmountForReservation = reservation.getTotalAmount();
            Double finalAmountCustomerPays = totalAmountForReservation; // Amount customer effectively pays after points

            if (totalAmountForReservation == null || totalAmountForReservation < 0) {
                throw new InvalidDataException("Reservation (ID: " + reservationId + ") does not have a valid total amount for payment processing.");
            }

            if (user != null && pointsToUse > 0) {
                finalAmountCustomerPays = totalAmountForReservation - (pointsToUse * 0.1);
                if (finalAmountCustomerPays < 0) {
                    finalAmountCustomerPays = 0.0;
                }
            }
            finalAmountCustomerPays = BigDecimal.valueOf(finalAmountCustomerPays).setScale(2, RoundingMode.HALF_UP).doubleValue();

            Double netAmountForOwner = 0.0;

            if (finalAmountCustomerPays > 0) {
                try {
                    long amountToChargeInSmallestUnit = Math.round(finalAmountCustomerPays * 100);
                    String chargeDescription = "Payment for Reservation ID: " + reservation.getId() +
                            " by user: " + (user != null ? user.getUsername() : "Guest") +
                            " for parking lot: " + parkingLot.getName();

                    System.out.println("Attempting Stripe charge for reservation " + reservationId + " with amount " + finalAmountCustomerPays + " RON (" + amountToChargeInSmallestUnit + " smallest unit)");

                    // Call StripeService to create the charge
                    Charge stripeCharge = stripeService.createPlatformCharge(amountToChargeInSmallestUnit, chargeDescription);

                    if (!"succeeded".equalsIgnoreCase(stripeCharge.getStatus())) {
                        String failureMessage = "Stripe charge attempt was not successful. Status: " + stripeCharge.getStatus();
                        System.err.println(failureMessage + " for Charge ID: " + stripeCharge.getId());
                        reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                        reservationRepository.save(reservation);
                        throw new PaymentProcessingException(failureMessage);
                    }
                    System.out.println("Stripe charge successful for reservation " + reservationId + ". Charge ID: " + stripeCharge.getId());

                    // Retrieve the BalanceTransaction to get the net amount
                    if (stripeCharge.getBalanceTransaction() != null) {
                        BalanceTransaction balanceTransaction = BalanceTransaction.retrieve(stripeCharge.getBalanceTransaction());
                        netAmountForOwner = BigDecimal.valueOf(balanceTransaction.getNet())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                                .doubleValue(); // Convert from smallest unit (e.g., bani) to RON
                        System.out.println("Net amount from Stripe (after fees) for Charge ID " + stripeCharge.getId() + ": " + netAmountForOwner + " RON");
                    } else {
                        // Fallback or error if balance transaction ID is not available, though it should be for successful charges.
                        // For simplicity, we might assume finalAmountCustomerPays is close enough if BT is missing,
                        // but ideally, this scenario should be handled robustly.
                        System.err.println("Warning: BalanceTransaction ID not found on successful charge " + stripeCharge.getId() + ". Using customer paid amount for owner earnings, which may not account for Stripe fees.");
                        netAmountForOwner = finalAmountCustomerPays;
                    }

                } catch (StripeException e) {
                    String stripeErrorMessage = (e.getStripeError() != null && e.getStripeError().getMessage() != null) ?
                            e.getStripeError().getMessage() : e.getMessage();
                    if (stripeErrorMessage == null) stripeErrorMessage = "An unknown Stripe error occurred during payment processing.";
                    System.err.println("StripeException during payment for reservation " + reservationId + ": " + stripeErrorMessage);
                    reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
                    reservationRepository.save(reservation);
                    throw new PaymentProcessingException(stripeErrorMessage);
                }
            } else {
                System.out.println("Final amount customer pays is 0 or less for reservation " + reservationId + ". Skipping Stripe charge. Net amount for owner is 0.");
                netAmountForOwner = 0.0;
            }

            reservation.setStatus(ReservationStatus.PAID);

            if (user != null && pointsToUse > 0) {
                Double currentLoyaltyPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
                user.setLoyaltyPoints(Math.max(0, currentLoyaltyPoints - pointsToUse));
            }
            reservation.setPointsUsed(pointsToUse);
            reservation.setFinalAmount(finalAmountCustomerPays);

            System.out.println("Reservation ID: " + reservationId + " - Checking owner earnings update.");
            System.out.println("Owner: " + (owner != null ? owner.getUsername() : "null"));
            System.out.println("Final Amount Customer Pays: " + finalAmountCustomerPays);
            System.out.println("Net Amount For Owner (calculated): " + netAmountForOwner);

            if (owner != null && netAmountForOwner > 0) {
                Double currentPendingEarnings = Optional.ofNullable(owner.getPendingEarnings()).orElse(0.0);
                Double currentTotalEarnings = Optional.ofNullable(owner.getTotalEarnings()).orElse(0.0);

                owner.setPendingEarnings(BigDecimal.valueOf(currentPendingEarnings).add(BigDecimal.valueOf(netAmountForOwner)).setScale(2, RoundingMode.HALF_UP).doubleValue());
                owner.setTotalEarnings(BigDecimal.valueOf(currentTotalEarnings).add(BigDecimal.valueOf(netAmountForOwner)).setScale(2, RoundingMode.HALF_UP).doubleValue());
                System.out.println("Updated owner " + owner.getUsername() + " pending earnings with net amount: " + netAmountForOwner);
                System.out.println("Updating pending earnings for owner: " + owner.getUsername() + " by " + netAmountForOwner);
            } else {
                System.out.println("Skipping pending earnings update for reservation " + reservationId + ". Owner is null or netAmountForOwner is not positive.");
            }

            if (user != null && finalAmountCustomerPays > 0) {
                double pointsToAddUnrounded = finalAmountCustomerPays * 0.05;
                BigDecimal pointsToAddBigDecimal = BigDecimal.valueOf(pointsToAddUnrounded)
                        .setScale(2, RoundingMode.HALF_UP);
                Double pointsToAdd = pointsToAddBigDecimal.doubleValue();
                Double currentLoyaltyPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);
                user.setLoyaltyPoints(currentLoyaltyPoints + pointsToAdd);
            }

            if (user != null) userRepository.save(user);
            if (owner != null && (user == null || !owner.getId().equals(user.getId()))) {
                userRepository.save(owner);
            }

            Reservation updatedReservation = reservationRepository.save(reservation);

            if (user == null) {
                OffsetDateTime tokenExpiry = updatedReservation.getEndTime() != null ? updatedReservation.getEndTime().plusHours(1) : OffsetDateTime.now().plusHours(24);
                GuestAccessToken guestToken = generateOrUpdateGuestToken(updatedReservation, tokenExpiry);
                guestAccessTokenString = guestToken.getToken();
            }

            if (recipientEmail != null && !recipientEmail.isEmpty()) {
                emailService.sendReservationConfirmationEmail(
                        recipientEmail,
                        updatedReservation.getId(),
                        parkingLot.getName(),
                        updatedReservation.getStartTime(),
                        updatedReservation.getEndTime(),
                        finalAmountCustomerPays,
                        guestAccessTokenString
                );
            }
            return reservationMapper.toDTO(updatedReservation);
        }
    }

    @Transactional(readOnly = true)
    public ReservationDTO getReservationByIdForGuest(String reservationId, String token) {
        GuestAccessToken accessToken = guestAccessTokenRepository.findByTokenAndReservationId(token, reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired access token for this reservation."));

        if (accessToken.getExpiresAt() != null && accessToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            guestAccessTokenRepository.delete(accessToken);
            throw new InvalidDataException("Access token has expired.");
        }
        return reservationMapper.toDTO(accessToken.getReservation());
    }

    @Transactional
    public void cleanupExpiredGuestAccessTokens() {
        guestAccessTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
    }

    @Transactional
    public ReviewDTO createReview(String reservationId, CreateReviewDTO reviewDto) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reviewRepository.existsByReservationId(reservationId)) {
            throw new ResourceAlreadyExistsException("A review already exists for this reservation.");
        }

        if (reservation.getStatus() != ReservationStatus.PAID) {
            throw new InvalidDataException("Reviews can only be added for paid or completed reservations. Current status: " + reservation.getStatus());
        }

        User reservationUser = reservation.getUser();
        String finalReviewerDisplayName;

        if (reservationUser != null) {
            finalReviewerDisplayName = reservationUser.getUsername();
        } else {
            if (reservation.getGuestName() != null && !reservation.getGuestName().isEmpty()) {
                finalReviewerDisplayName = reservation.getGuestName();
            } else {
                finalReviewerDisplayName = "Guest";
            }
        }

        Review review = new Review();
        review.setRating(reviewDto.getRating());
        review.setComment(reviewDto.getComment());
        review.setReservation(reservation);
        review.setUser(reservationUser);
        review.setReviewerDisplayName(finalReviewerDisplayName);

        Review savedReview = reviewRepository.save(review);

        ParkingLot parkingLot = reservation.getParkingLot();
        if (parkingLot == null) {
            throw new ResourceNotFoundException("Trying to add a review for a reservation associated with a parking lot that doesn't exist." + reservationId);
        } else {
            recalculateAndSaveParkingLotAverageRating(parkingLot);
        }

        return ReviewDTO.builder()
                .id(savedReview.getId())
                .rating(savedReview.getRating())
                .comment(savedReview.getComment())
                .reservationId(savedReview.getReservation().getId())
                .userId(savedReview.getUser() != null ? savedReview.getUser().getId() : null)
                .reviewerDisplayName(savedReview.getReviewerDisplayName())
                .createdAt(savedReview.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public ReviewDTO getReviewByReservationId(String reservationId) {
        Review review = reviewRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        return ReviewDTO.builder()
                .id(review.getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .reservationId(review.getReservation().getId())
                .userId(review.getUser() != null ? review.getUser().getId() : null)
                .reviewerDisplayName(review.getReviewerDisplayName())
                .createdAt(review.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ReviewDTO> getReviewsByParkingLotId(String parkingLotId, Pageable pageable) {
        parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found with ID: " + parkingLotId));

        Page<Review> reviewsPage = reviewRepository.findByReservationParkingLotId(parkingLotId, pageable);

        List<ReviewDTO> reviewDTOs = reviewsPage.getContent().stream()
                .map(review -> ReviewDTO.builder()
                        .id(review.getId())
                        .rating(review.getRating())
                        .comment(review.getComment())
                        .reservationId(review.getReservation().getId())
                        .userId(review.getUser() != null ? review.getUser().getId() : null)
                        .reviewerDisplayName(review.getReviewerDisplayName())
                        .createdAt(review.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageImpl<>(reviewDTOs, pageable, reviewsPage.getTotalElements());
    }

    @Transactional
    public void deleteReview(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with ID: " + reviewId));
        try {
            reviewRepository.delete(review);
            reviewRepository.flush();
        } catch (Exception e) {
            throw e;
        }
        ParkingLot parkingLot = review.getReservation().getParkingLot();
        recalculateAndSaveParkingLotAverageRating(parkingLot);
    }

    @Transactional
    protected void recalculateAndSaveParkingLotAverageRating(ParkingLot parkingLot) {
        if (parkingLot == null) {
            throw new ResourceNotFoundException("Parking lot cannot be null for rating recalculation.");
        }

        List<Review> reviews = reviewRepository.findByReservationParkingLotId(parkingLot.getId());

        if (reviews.isEmpty()) {
            parkingLot.setAverageRating(0.0);
        } else {
            long sumOfRatings = 0;
            for (Review r : reviews) {
                sumOfRatings += r.getRating();
            }
            double newAverage = (double) sumOfRatings / reviews.size();
            BigDecimal bdAverage = BigDecimal.valueOf(newAverage);
            BigDecimal roundedAverage = bdAverage.setScale(2, RoundingMode.HALF_UP);

            parkingLot.setAverageRating(roundedAverage.doubleValue());
        }
        parkingLotRepository.save(parkingLot);
    }

    @Transactional
    public QrTokenResponseDTO generateActiveQrToken(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (!(reservation.getStatus() == ReservationStatus.PAID ||
                reservation.getStatus() == ReservationStatus.ACTIVE)) {
            throw new InvalidDataException("Cannot generate QR token for reservation " + reservationId +
                    " with status: " + reservation.getStatus() + ". Expected PAID or ACTIVE.");
        }

        if (reservation.isHasCheckedIn() && reservation.isHasCheckedOut()) {
            throw new InvalidDataException("Reservation " + reservationId + " is already completed. Cannot generate new QR token.");
        }

        String newActiveQrToken = UUID.randomUUID().toString();
        OffsetDateTime newQrTokenExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);

        reservation.setActiveQrToken(newActiveQrToken);
        reservation.setQrTokenExpiry(newQrTokenExpiry);
        reservationRepository.save(reservation);

        String qrCodePayload = reservation.getId() + ":" + newActiveQrToken;

        return QrTokenResponseDTO.builder()
                .reservationId(reservation.getId())
                .activeQrToken(newActiveQrToken)
                .qrTokenExpiry(newQrTokenExpiry)
                .qrCodePayload(qrCodePayload)
                .build();
    }
}