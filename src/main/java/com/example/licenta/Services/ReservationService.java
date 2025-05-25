package com.example.licenta.Services;

import com.example.licenta.DTOs.CreateReservationDTO;
import com.example.licenta.DTOs.CreateReviewDTO;
import com.example.licenta.DTOs.ReservationDTO;
import com.example.licenta.DTOs.ReviewDTO;
import com.example.licenta.Enum.ParkingLot.PricingType;
import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Exceptions.ResourceAlreadyExistsException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Mappers.ReservationMapper;
import com.example.licenta.Models.*;
import com.example.licenta.Repositories.*;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
                              ParkingLotRepository parkingLotRepository,
                              UserRepository userRepository,
                              ReservationMapper reservationMapper,
                              OpenAiChatModel openAiChatModel,
                              EmailService emailService,
                              ReviewRepository reviewRepository,
                              GuestAccessTokenRepository guestAccessTokenRepository) {
        this.reservationRepository = reservationRepository;
        this.parkingLotRepository = parkingLotRepository;
        this.userRepository = userRepository;
        this.reservationMapper = reservationMapper;
        this.openAiChatModel = openAiChatModel;
        this.emailService = emailService;
        this.reviewRepository = reviewRepository;
        this.guestAccessTokenRepository = guestAccessTokenRepository;
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
                        "predict a fair market price in RON (Romanian Leu). Parking is generally not free. " +
                        "Respond ONLY with the numerical price value (e.g., 25.50 or 30). Do not provide any other text or explanation. " +
                        "For context, a short stay (1-2 hours) in a central urban lot might be 5-20 RON, while a longer stay in a peripheral lot might be 5-15 RON per few hours. " +
                        "If you cannot determine a price, respond with a typical small non-zero placeholder like 10.00 rather than 0.00.";

                String userMessageContent = String.format(
                        "Predict the parking price for the following:\n" +
                                "Parking Lot Name: %s\n" +
                                "Parking Lot Address (is in Romania): %s\n" +
                                "Parking Lot Category: %s\n" +
                                "Total Spots: %d\n" +
                                "Requested Start Time: %s\n" +
                                "Requested End Time: %s\n" +
                                "Current Date and Time (UTC): %s\n" +
                                "Consider factors like demand, time of day, day of the week, and parking lot features for a dynamic price.",
                        parkingLot.getName(),
                        parkingLot.getAddress(),
                        parkingLot.getCategory() != null ? parkingLot.getCategory().name() : "N/A",
                        parkingLot.getTotalSpots() != null ? parkingLot.getTotalSpots() : 0,
                        startTime.toString(),
                        endTime.toString(),
                        OffsetDateTime.now().toString()
                );

                Prompt prompt = new Prompt(List.of(
                        new SystemMessage(systemMessageContent),
                        new UserMessage(userMessageContent)
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

    @Transactional
    public ReservationDTO updateReservationStatus(String reservationId, ReservationStatus newStatus, Integer pointsUsed, Double finalAmount) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        reservation.setStatus(newStatus);

        if (pointsUsed != null) {
            reservation.setPointsUsed(pointsUsed);
        }
        if (finalAmount != null) {
            reservation.setFinalAmount(finalAmount);
        }

        Reservation updatedReservation = reservationRepository.save(reservation);

        // TODO: Add side effects based on status change (e.g., sending notifications)

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
    public ReservationDTO handleSuccessfulPayment(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        ParkingLot parkingLot = reservation.getParkingLot();
        if (parkingLot == null) {
            throw new InvalidDataException("Critical error: Reservation (ID: " + reservationId + ") is not associated with a parking lot.");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new InvalidDataException("Reservation (ID: " + reservationId + ") is not in PENDING_PAYMENT status. Current status: " + reservation.getStatus());
        }

        User user = reservation.getUser();
        String recipientEmail = (user != null && user.getEmail() != null) ?
                user.getEmail() : reservation.getGuestEmail();
        String guestAccessTokenString = null;

        if (reservation.getReservationType() == ReservationType.PAY_FOR_USAGE && reservation.getEndTime() == null) {
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

        } else {
            if (reservation.getEndTime() == null && reservation.getReservationType() != ReservationType.PAY_FOR_USAGE) {
                throw new InvalidDataException("Reservation (ID: " + reservationId + ") of type " +
                        reservation.getReservationType() + " must have an end time for final payment processing.");
            }

            if (user == null) {
                OffsetDateTime tokenExpiry = null;
                if (reservation.getEndTime() != null) {
                    tokenExpiry = reservation.getEndTime().plusHours(1);
                } else {
                    tokenExpiry = OffsetDateTime.now().plusHours(1);
                    System.err.println("Guest reservation " + reservationId + " of type " + reservation.getReservationType() + " has no endTime for token generation. Setting default expiry.");
                }
                GuestAccessToken guestToken = generateOrUpdateGuestToken(reservation, tokenExpiry);
                guestAccessTokenString = guestToken.getToken();
            }

            User owner = parkingLot.getOwner();
            Double amountPaid = reservation.getFinalAmount();

            if (amountPaid == null) {
                throw new InvalidDataException("Reservation (ID: " + reservationId + ") does not have a final amount for payment processing.");
            }
            if (amountPaid  < 0) {
                throw new InvalidDataException("Reservation (ID: " + reservationId + ") final amount cannot be negative.");
            }

            reservation.setStatus(ReservationStatus.PAID);

            if (owner != null && amountPaid.compareTo(0.0) > 0) {
                Double currentPendingEarnings = Optional.ofNullable(owner.getPendingEarnings()).orElse(0.0);
                Double currentTotalEarnings = Optional.ofNullable(owner.getTotalEarnings()).orElse(0.0);
                owner.setPendingEarnings(currentPendingEarnings + amountPaid);
                owner.setTotalEarnings(currentTotalEarnings + amountPaid);
                userRepository.save(owner);
            } else if (owner == null && amountPaid.compareTo(0.0) > 0) {
                System.err.println("Parking lot ID " + parkingLot.getId() + " for reservation ID " + reservationId + " does not have an owner. Earnings not recorded.");
            }

            if (user != null && amountPaid.compareTo(0.0) > 0) {
                double pointsToAddUnrounded = amountPaid * 0.05;

                BigDecimal pointsToAddBigDecimal = BigDecimal.valueOf(pointsToAddUnrounded)
                        .setScale(2, RoundingMode.HALF_UP);
                Double pointsToAdd = pointsToAddBigDecimal.doubleValue();

                Double currentLoyaltyPoints = Optional.ofNullable(user.getLoyaltyPoints()).orElse(0.0);

                user.setLoyaltyPoints(currentLoyaltyPoints + pointsToAdd);
                userRepository.save(user);
                System.out.println("Awarded " + pointsToAdd + " loyalty points to user " + user.getId() + ". New total: " + user.getLoyaltyPoints());
            }

            Reservation updatedReservation = reservationRepository.save(reservation);

            if (recipientEmail != null && !recipientEmail.isEmpty()) {
                emailService.sendReservationConfirmationEmail(
                        recipientEmail,
                        updatedReservation.getId(),
                        parkingLot.getName(),
                        updatedReservation.getStartTime(),
                        updatedReservation.getEndTime(),
                        amountPaid,
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

        Page<Review> reviewsPage = reviewRepository.findReviewsByParkingLotId(parkingLotId, pageable);

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

        List<Review> reviews = reviewRepository.findAllByReservationParkingLotId(parkingLot.getId());

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
}