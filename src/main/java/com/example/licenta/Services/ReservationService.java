package com.example.licenta.Services;

import com.example.licenta.DTOs.CreateReservationDTO;
import com.example.licenta.DTOs.ReservationDTO;
import com.example.licenta.Enum.ParkingLot.PricingType;
import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Mappers.ReservationMapper;
import com.example.licenta.Models.GuestAccessToken;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.Reservation;
import com.example.licenta.Models.User;
import com.example.licenta.Repositories.GuestAccessTokenRepository;
import com.example.licenta.Repositories.ParkingLotRepository;
import com.example.licenta.Repositories.ReservationRepository;
import com.example.licenta.Repositories.UserRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Random;
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

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
                              ParkingLotRepository parkingLotRepository,
                              UserRepository userRepository,
                              ReservationMapper reservationMapper,
                              OpenAiChatModel openAiChatModel,
                              EmailService emailService,
                              GuestAccessTokenRepository guestAccessTokenRepository) {
        this.reservationRepository = reservationRepository;
        this.parkingLotRepository = parkingLotRepository;
        this.userRepository = userRepository;
        this.reservationMapper = reservationMapper;
        this.openAiChatModel = openAiChatModel;
        this.emailService = emailService;
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

            if(dto.getEndTime() != null ){
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
    public ReservationDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
        return reservationMapper.toDTO(reservation);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePrice(Long parkingLotId, OffsetDateTime startTime, OffsetDateTime endTime) {
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
                        BigDecimal predictedPrice = new BigDecimal(cleanedPriceStr).setScale(2, RoundingMode.HALF_UP);
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

    private BigDecimal generateRandomPrice(Long parkingLotId, OffsetDateTime startTime, OffsetDateTime endTime) {
        double randomPriceValue = 1.0 + (100.0 * random.nextDouble());
        BigDecimal price = BigDecimal.valueOf(randomPriceValue).setScale(2, RoundingMode.HALF_UP);
        System.out.println("Generated Fallback/Mock Price: " + price + " for Lot ID: " + parkingLotId + " Start: " + startTime + " End: " + endTime);
        return price;
    }

    @Transactional(readOnly = true)
    public Page<ReservationDTO> getReservationsByUserId(Long userId, List<ReservationType> types, Pageable pageable) {
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
    public ReservationDTO updateReservationStatus(Long reservationId, ReservationStatus newStatus, Integer pointsUsed, BigDecimal finalAmount) {
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
    public Optional<ReservationDTO> findActiveReservation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        OffsetDateTime now = OffsetDateTime.now();

        Optional<Reservation> standardActive = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatusOrderByStartTimeDesc(
                userId,
                ReservationType.STANDARD,
                now,
                now,
                ReservationStatus.PAID
        );

        Optional<Reservation> payForUsageActive = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeIsNullAndStatusOrderByStartTimeDesc(
                userId,
                ReservationType.PAY_FOR_USAGE,
                now,
                ReservationStatus.ACTIVE
        );

        if (standardActive.isPresent() && payForUsageActive.isPresent()) {
            Reservation standard = standardActive.get();
            Reservation payForUsage = payForUsageActive.get();

            return Optional.of(reservationMapper.toDTO(
                    standard.getStartTime().isAfter(payForUsage.getStartTime()) ? standard : payForUsage
            ));
        }

        if (standardActive.isPresent()) {
            return Optional.of(reservationMapper.toDTO(standardActive.get()));
        }

        if (payForUsageActive.isPresent()) {
            return Optional.of(reservationMapper.toDTO(payForUsageActive.get()));
        }

        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ReservationDTO> findUpcomingReservation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        OffsetDateTime now = OffsetDateTime.now();

        Optional<Reservation> standardUpcoming = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeAfterAndStatusOrderByStartTimeAsc(
                userId,
                ReservationType.STANDARD,
                now,
                ReservationStatus.PAID
        );

        Optional<Reservation> payForUsageUpcoming = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeAfterAndStatusOrderByStartTimeAsc(
                userId,
                ReservationType.PAY_FOR_USAGE,
                now,
                ReservationStatus.ACTIVE
        );

        if (standardUpcoming.isPresent() && payForUsageUpcoming.isPresent()) {
            Reservation standard = standardUpcoming.get();
            Reservation payForUsage = payForUsageUpcoming.get();

            return Optional.of(reservationMapper.toDTO(
                    standard.getStartTime().isBefore(payForUsage.getStartTime()) ? standard : payForUsage
            ));
        }

        if (standardUpcoming.isPresent()) {
            return Optional.of(reservationMapper.toDTO(standardUpcoming.get()));
        }

        if (payForUsageUpcoming.isPresent()) {
            return Optional.of(reservationMapper.toDTO(payForUsageUpcoming.get()));
        }

        return Optional.empty();
    }

    @Transactional
    public ReservationDTO endActiveReservation(Long reservationId, OffsetDateTime endTime, BigDecimal totalAmount) {
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
    public ReservationDTO handleSuccessfulPayment(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        ParkingLot parkingLot = reservation.getParkingLot();
        if (parkingLot == null) {
            throw new InvalidDataException("Reservation (ID: " + reservationId + ") is not associated with a parking lot.");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new InvalidDataException("Reservation (ID: " + reservationId + ") is not in PENDING_PAYMENT status. Current status: " + reservation.getStatus());
        }

        User user = reservation.getUser();

        String recipientEmail = (user != null && user.getEmail() != null) ?
                user.getEmail() : reservation.getGuestEmail();

        String guestAccessToken = null;
        if (user == null) {
            guestAccessToken = UUID.randomUUID().toString();
            GuestAccessToken tokenEntity = GuestAccessToken.builder()
                    .token(guestAccessToken)
                    .reservation(reservation)
                    .expiresAt(reservation.getEndTime().plus(1, ChronoUnit.HOURS))
                    .build();
            guestAccessTokenRepository.save(tokenEntity);
        }

        // (card verification)
        if (reservation.getReservationType() == ReservationType.PAY_FOR_USAGE && reservation.getEndTime() == null) {
            reservation.setStatus(ReservationStatus.ACTIVE);
            Reservation updatedReservation = reservationRepository.save(reservation);
            if (recipientEmail != null && !recipientEmail.isEmpty()) {
                emailService.sendPayForUsageActiveEmail(
                        recipientEmail,
                        updatedReservation.getId(),
                        parkingLot.getName(),
                        updatedReservation.getStartTime(),
                        guestAccessToken
                );
            }
            return reservationMapper.toDTO(updatedReservation);
        } else {
            User owner = parkingLot.getOwner();
            BigDecimal amountPaid = reservation.getFinalAmount();

            if (amountPaid == null) {
                throw new InvalidDataException("Reservation (ID: " + reservationId + ") does not have a final amount for payment processing.");
            }
            if (amountPaid.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidDataException("Reservation (ID: " + reservationId + ") final amount cannot be negative.");
            }

            reservation.setStatus(ReservationStatus.PAID);

            if (owner != null && amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentPendingEarnings = Optional.ofNullable(owner.getPendingEarnings()).orElse(BigDecimal.ZERO);
                BigDecimal currentTotalEarnings = Optional.ofNullable(owner.getTotalEarnings()).orElse(BigDecimal.ZERO);
                owner.setPendingEarnings(currentPendingEarnings.add(amountPaid));
                owner.setTotalEarnings(currentTotalEarnings.add(amountPaid));
                userRepository.save(owner);
            } else if (owner == null && amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                System.err.println("Parking lot ID " + parkingLot.getId() + " for reservation ID " + reservationId + " does not have an owner. Earnings not recorded.");
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
                        guestAccessToken
                );
            }
            return reservationMapper.toDTO(updatedReservation);
        }
    }

    @Transactional(readOnly = true)
    public ReservationDTO getReservationByIdForGuest(Long reservationId, String token) {
        GuestAccessToken accessToken = guestAccessTokenRepository.findByTokenAndReservationId(token, reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired access token for this reservation."));

        if (accessToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            guestAccessTokenRepository.delete(accessToken);
            throw new InvalidDataException("Access token has expired.");
        }
        return reservationMapper.toDTO(accessToken.getReservation());
    }

    @Transactional
    public void cleanupExpiredGuestAccessTokens() {
        guestAccessTokenRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
       }
}