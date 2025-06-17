package com.example.licenta.Services;

import com.example.licenta.DTOs.*;
import com.example.licenta.Enum.ParkingLot.DayOfWeek;
import com.example.licenta.Enum.ParkingLot.ExtensionPricingModel;
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
import com.stripe.model.PaymentIntent;
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
            return calculateFixedPrice(parkingLotId, startTime, endTime);
        }
    }

    private double calculateOccupancyRate(ParkingLot parkingLot) {
        if (parkingLot.getTotalSpots() == null || parkingLot.getTotalSpots() == 0) return 0.0;
        int occupied = parkingLot.getTotalSpots() - parkingLot.getSpotsAvailable();
        return (occupied * 100.0) / parkingLot.getTotalSpots();
    }

    private Double calculateFixedPrice(String parkingLotId, OffsetDateTime startTime, OffsetDateTime endTime) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new InvalidDataException("Parking lot not found"));

        long totalMinutes = Duration.between(startTime, endTime).toMinutes();

        OffsetDateTime billableStartTime = startTime;
        if (parkingLot.isHasFreeTime() && parkingLot.getFreeTimeMinutes() != null && parkingLot.getFreeTimeMinutes() > 0) {
            billableStartTime = startTime.plusMinutes(parkingLot.getFreeTimeMinutes());
            if (billableStartTime.isAfter(endTime) || billableStartTime.isEqual(endTime)) {
                return 0.0;
            }
        }

        double totalPrice = 0.0;
        OffsetDateTime currentTime = billableStartTime;

        while (currentTime.isBefore(endTime)) {
            DayOfWeek currentDay = getDayOfWeekFromOffsetDateTime(currentTime);

            PriceInterval applicableInterval = findApplicablePriceInterval(
                    parkingLot.getPriceIntervals(),
                    currentTime,
                    currentDay
            );

            if (applicableInterval == null) {
                throw new InvalidDataException("No price interval found for the given time and day");
            }

            OffsetDateTime intervalEndTime = getIntervalEndTime(currentTime, applicableInterval, endTime);
            long intervalMinutes = Duration.between(currentTime, intervalEndTime).toMinutes();

            if (applicableInterval.getDuration() != null && applicableInterval.getDuration() > 0) {
                double durationUnits = Math.ceil((double) intervalMinutes / applicableInterval.getDuration());
                totalPrice += durationUnits * applicableInterval.getPrice();
            } else {
                totalPrice += (intervalMinutes / 60.0) * applicableInterval.getPrice();
            }

            currentTime = intervalEndTime;
        }

        return totalPrice;
    }

    private DayOfWeek getDayOfWeekFromOffsetDateTime(OffsetDateTime dateTime) {
        java.time.DayOfWeek javaDayOfWeek = dateTime.getDayOfWeek();
        switch (javaDayOfWeek) {
            case MONDAY: return DayOfWeek.MONDAY;
            case TUESDAY: return DayOfWeek.TUESDAY;
            case WEDNESDAY: return DayOfWeek.WEDNESDAY;
            case THURSDAY: return DayOfWeek.THURSDAY;
            case FRIDAY: return DayOfWeek.FRIDAY;
            case SATURDAY: return DayOfWeek.SATURDAY;
            case SUNDAY: return DayOfWeek.SUNDAY;
            default: throw new InvalidDataException("Invalid day of week");
        }
    }

    private PriceInterval findApplicablePriceInterval(List<PriceInterval> intervals, OffsetDateTime currentTime, DayOfWeek dayOfWeek) {
        String timeString = String.format("%02d:%02d", currentTime.getHour(), currentTime.getMinute());

        for (PriceInterval interval : intervals) {
            if (interval.getDays().contains(dayOfWeek)) {
                if (isTimeWithinInterval(timeString, interval.getStartTime(), interval.getEndTime())) {
                    return interval;
                }
            }
        }
        return null;
    }

    private boolean isTimeWithinInterval(String currentTime, String startTime, String endTime) {
        int current = timeToMinutes(currentTime);
        int start = timeToMinutes(startTime);
        int end = timeToMinutes(endTime);

        if (end < start) {
            return current >= start || current < end;
        } else {
            return current >= start && current < end;
        }
    }

    private int timeToMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private OffsetDateTime getIntervalEndTime(OffsetDateTime currentTime, PriceInterval interval, OffsetDateTime endTime) {
        String[] endTimeParts = interval.getEndTime().split(":");
        int endHour = Integer.parseInt(endTimeParts[0]);
        int endMinute = Integer.parseInt(endTimeParts[1]);

        OffsetDateTime intervalEnd = currentTime.withHour(endHour).withMinute(endMinute).withSecond(0).withNano(0);

        if (intervalEnd.isBefore(currentTime) || intervalEnd.isEqual(currentTime)) {
            intervalEnd = intervalEnd.plusDays(1);
        }

        return intervalEnd.isBefore(endTime) ? intervalEnd : endTime;
    }

    public boolean canExtendReservation(String parkingLotId, ReservationType reservationType) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new InvalidDataException("Parking lot not found"));

        boolean isRegularReservation = reservationType == ReservationType.STANDARD || reservationType == ReservationType.DIRECT;

        if (isRegularReservation) {
            return parkingLot.isAllowExtensionsForRegular();
        } else {
            return parkingLot.isAllowExtensionsForOnTheSpot();
        }
    }

    public Double calculateExtensionPrice(String parkingLotId, ReservationType reservationType,
                                          OffsetDateTime originalEndTime, OffsetDateTime newEndTime) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new InvalidDataException("Parking lot not found"));

        if (!canExtendReservation(parkingLotId, reservationType)) {
            throw new InvalidDataException("Extensions not allowed for this reservation type");
        }

        boolean isRegularReservation = reservationType == ReservationType.STANDARD || reservationType == ReservationType.DIRECT;

        long extensionMinutes = Duration.between(originalEndTime, newEndTime).toMinutes();

        Integer maxExtensionTime = isRegularReservation ?
                parkingLot.getMaxExtensionTimeForRegular() :
                parkingLot.getMaxExtensionTimeForOnTheSpot();

        if (maxExtensionTime != null && extensionMinutes > maxExtensionTime) {
            throw new InvalidDataException("Extension duration exceeds maximum allowed time");
        }

        Double baseExtensionPrice = calculateFixedPrice(parkingLotId, originalEndTime, newEndTime);

        ExtensionPricingModel pricingModel = isRegularReservation ?
                parkingLot.getExtensionPricingModelForRegular() :
                parkingLot.getExtensionPricingModelForOnTheSpot();

        Double pricingPercentage = isRegularReservation ?
                parkingLot.getExtensionPricingPercentageForRegular() :
                parkingLot.getExtensionPricingPercentageForOnTheSpot();

        if (pricingModel == ExtensionPricingModel.HIGHER && pricingPercentage != null) {
            baseExtensionPrice *= (1 + pricingPercentage / 100.0);
        }

        return baseExtensionPrice;
    }

    public boolean canCancelReservation(String parkingLotId, OffsetDateTime reservationStartTime,
                                        OffsetDateTime currentTime, boolean hasReservationStarted) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new InvalidDataException("Parking lot not found"));

        if (!parkingLot.isAllowCancellations()) {
            return false;
        }

        if (!hasReservationStarted) {
            if (!parkingLot.isAllowPreReservationCancellations()) {
                return false;
            }

            Integer cancelWindow = parkingLot.getPreReservationCancelWindow();
            if (cancelWindow != null) {
                long minutesUntilStart = Duration.between(currentTime, reservationStartTime).toMinutes();
                return minutesUntilStart >= cancelWindow;
            }
            return true;
        } else {
            if (!parkingLot.isAllowMidReservationCancellations()) {
                return false;
            }

            Integer cancelWindow = parkingLot.getMidReservationCancelWindow();
            if (cancelWindow != null) {
                long minutesSinceStart = Duration.between(reservationStartTime, currentTime).toMinutes();
                return minutesSinceStart <= cancelWindow;
            }
            return true;
        }
    }

    public Double calculateCancellationFee(String parkingLotId, OffsetDateTime reservationStartTime,
                                           OffsetDateTime currentTime, boolean hasReservationStarted,
                                           Double originalReservationPrice) {
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new InvalidDataException("Parking lot not found"));

        if (!canCancelReservation(parkingLotId, reservationStartTime, currentTime, hasReservationStarted)) {
            throw new InvalidDataException("Cancellation not allowed for this reservation");
        }

        Double cancellationFee = 0.0;

        if (!hasReservationStarted) {
            if (parkingLot.isApplyPreCancelFee() && parkingLot.getPreReservationCancelFee() != null) {
                cancellationFee = parkingLot.getPreReservationCancelFee();
            }
        } else {
            if (parkingLot.isApplyMidCancelFee() && parkingLot.getMidReservationCancelFee() != null) {
                cancellationFee = parkingLot.getMidReservationCancelFee();
            }
        }

        return cancellationFee;
    }

    public Double calculateTotalCancellationAmount(String parkingLotId, OffsetDateTime reservationStartTime,
                                                   OffsetDateTime currentTime, boolean hasReservationStarted,
                                                   Double originalReservationPrice) {
        Double cancellationFee = calculateCancellationFee(parkingLotId, reservationStartTime,
                currentTime, hasReservationStarted, originalReservationPrice);

        if (!hasReservationStarted) {
            return cancellationFee;
        } else {
            OffsetDateTime actualEndTime = currentTime;
            Double usedTimePrice = calculateFixedPrice(parkingLotId, reservationStartTime, actualEndTime);

            return usedTimePrice + cancellationFee;
        }
    }

    @Transactional
    public ReservationDTO extendReservation(String reservationId, OffsetDateTime newEndTime) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new InvalidDataException("Reservation not found"));

        if (!canExtendReservation(reservation.getParkingLot().getId(), reservation.getReservationType())) {
            throw new InvalidDataException("Extension not allowed for this reservation");
        }

        if (reservation.getReservationType() != ReservationType.STANDARD &&
                reservation.getReservationType() != ReservationType.DIRECT) {
            throw new InvalidDataException("Extensions are only allowed for STANDARD and DIRECT reservations");
        }

        if (reservation.getStatus() != ReservationStatus.PAID &&
                reservation.getStatus() != ReservationStatus.ACTIVE &&
                reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new InvalidDataException("Cannot extend reservation with status: " + reservation.getStatus());
        }

        OffsetDateTime currentEndTime = reservation.getEndTime();
        Double extensionPrice = calculateExtensionPrice(
                reservation.getParkingLot().getId(),
                reservation.getReservationType(),
                currentEndTime,
                newEndTime
        );

        if (reservation.getStatus() == ReservationStatus.PAID) {
            try {
                String stripeCustomerId = reservation.getStripeCustomerId();
                if (stripeCustomerId == null) {
                    stripeCustomerId = stripeService.getOrCreateStripeCustomerId(
                            reservation.getUser(),
                            reservation.getGuestEmail()
                    );
                    reservation.setStripeCustomerId(stripeCustomerId);
                }

                long extensionAmountInBani = Math.round(extensionPrice * 100);

                Map<String, String> metadata = new HashMap<>();
                metadata.put("reservation_id", reservationId);
                metadata.put("extension_amount", extensionPrice.toString());
                metadata.put("type", "extension");

                StripeIntentResponse intentResponse;
                intentResponse = stripeService.createPaymentIntent(
                        extensionAmountInBani,
                        "RON",
                        stripeCustomerId,
                        null,
                        metadata,
                        false
                );

                reservation.setStripeExtensionPaymentIntentId(intentResponse.getIntentId());

                reservation.setEndTime(newEndTime);
                reservation.setTotalAmount(reservation.getTotalAmount() + extensionPrice);
                reservation.setFinalAmount(reservation.getFinalAmount() + extensionPrice);
                reservation.setUpdatedAt(OffsetDateTime.now());

                System.out.println("Extension payment intent created: " + intentResponse.getIntentId() +
                        " for reservation: " + reservationId + " with amount: " + extensionPrice);

            } catch (Exception e) {
                System.err.println("Error creating extension payment intent: " + e.getMessage());
                throw new InvalidDataException("Failed to process extension payment: " + e.getMessage());
            }
        } else {
            OffsetDateTime originalEndTime = reservation.getOriginalEndTime() != null
                    ? reservation.getOriginalEndTime()
                    : reservation.getEndTime();

            long additionalMinutes = Duration.between(currentEndTime, newEndTime).toMinutes();
            reservation.setExtendedTimeMinutes(reservation.getExtendedTimeMinutes() + additionalMinutes);

            reservation.setEndTime(newEndTime);
            if (reservation.getOriginalEndTime() == null) {
                reservation.setOriginalEndTime(currentEndTime);
            }

            Double totalPrice = calculateTotalPriceWithExtensions(reservation);
            reservation.setTotalAmount(totalPrice);
            reservation.setFinalAmount(totalPrice - reservation.getPointsUsed());
            reservation.setUpdatedAt(OffsetDateTime.now());

            System.out.println("Extended unpaid reservation: " + reservationId +
                    " by " + additionalMinutes + " minutes. New total: " + totalPrice);
        }

        return reservationMapper.toDTO(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationDTO cancelReservation(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new InvalidDataException("Reservation not found"));

        OffsetDateTime currentTime = OffsetDateTime.now();
        boolean hasStarted = currentTime.isAfter(reservation.getStartTime());

        if (!canCancelReservation(
                reservation.getParkingLot().getId(),
                reservation.getStartTime(),
                currentTime,
                hasStarted
        )) {
            throw new InvalidDataException("Cancellation not allowed for this reservation");
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new InvalidDataException("Reservation is already cancelled");
        }

        Double totalCancellationAmount = calculateTotalCancellationAmount(
                reservation.getParkingLot().getId(),
                reservation.getStartTime(),
                currentTime,
                hasStarted,
                reservation.getTotalAmount()
        );

        // Handle refunds for PAID reservations (STANDARD and DIRECT only)
        if (reservation.getStatus() == ReservationStatus.PAID &&
                (reservation.getReservationType() == ReservationType.STANDARD ||
                        reservation.getReservationType() == ReservationType.DIRECT)) {

            try {
                Double refundAmount = reservation.getTotalAmount() - totalCancellationAmount;

                if (refundAmount > 0) {
                    String paymentIntentId = reservation.getStripePaymentIntentId();
                    if (paymentIntentId != null) {
                        long refundAmountInBani = Math.round(refundAmount * 100);

                        // Create refund using Stripe API
                        String refundId = processStripeRefund(paymentIntentId, refundAmountInBani, reservationId);

                        reservation.setStripeRefundId(refundId);
                        reservation.setRefundAmount(refundAmount);

                        System.out.println("Refund processed: " + refundId + " for reservation: " + reservationId +
                                " with amount: " + refundAmount);
                    } else {
                        System.err.println("Warning: No payment intent ID found for paid reservation: " + reservationId);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing refund for reservation " + reservationId + ": " + e.getMessage());
            }
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setTotalAmount(totalCancellationAmount);
        reservation.setFinalAmount(totalCancellationAmount);
        reservation.setUpdatedAt(OffsetDateTime.now());

        if (hasStarted) {
            reservation.setEndTime(currentTime);
        }

//        ParkingLot parkingLot = reservation.getParkingLot();
//        if (parkingLot.getSpotsAvailable() != null) {
//            parkingLot.setSpotsAvailable(parkingLot.getSpotsAvailable() + 1);
//            parkingLotRepository.save(parkingLot);
//        }

        return reservationMapper.toDTO(reservationRepository.save(reservation));
    }

    private String processStripeRefund(String paymentIntentId, long refundAmountInBani, String reservationId) {
        try {
            PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(paymentIntentId);

            if (paymentIntent.getLatestChargeObject() != null) {
                String chargeId = paymentIntent.getLatestChargeObject().getId();

                Map<String, Object> refundParams = new HashMap<>();
                refundParams.put("charge", chargeId);
                refundParams.put("amount", refundAmountInBani);

                Map<String, String> metadata = new HashMap<>();
                metadata.put("reservation_id", reservationId);
                metadata.put("refund_reason", "reservation_cancelled");
                refundParams.put("metadata", metadata);

                com.stripe.model.Refund refund = com.stripe.model.Refund.create(refundParams);

                return refund.getId();
            } else {
                throw new PaymentProcessingException("No charges found for payment intent: " + paymentIntentId);
            }
        } catch (Exception e) {
            throw new PaymentProcessingException("Failed to process Stripe refund: " + e.getMessage());
        }
    }

    private Double calculateTotalPriceWithExtensions(Reservation reservation) {
        if (reservation.getOriginalEndTime() == null) {
            return calculatePrice(
                    reservation.getParkingLot().getId(),
                    reservation.getStartTime(),
                    reservation.getEndTime()
            );
        }

        Double originalPrice = calculatePrice(
                reservation.getParkingLot().getId(),
                reservation.getStartTime(),
                reservation.getOriginalEndTime()
        );

        Double extensionPrice = calculatePrice(
                reservation.getParkingLot().getId(),
                reservation.getOriginalEndTime(),
                reservation.getEndTime()
        );

        return originalPrice + extensionPrice;
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

        try {
            String stripeCustomerId = stripeService.getOrCreateStripeCustomerId(user, reservation.getGuestEmail());
            if (user != null && (user.getStripeCustomerId() == null || !user.getStripeCustomerId().equals(stripeCustomerId))) {
                user.setStripeCustomerId(stripeCustomerId);
            }
            reservation.setStripeCustomerId(stripeCustomerId);

            Map<String, String> setupIntentMetadata = Map.of(
                    "reservation_id", reservation.getId(),
                    "user_id", user != null ? user.getId() : "guest-" + reservation.getId(),
                    "parking_lot_id", reservation.getParkingLot() != null ? reservation.getParkingLot().getId() : "N/A",
                    "intent_purpose", "pay_for_usage_card_setup"
            );

            StripeIntentResponse stripeResponse = stripeService.createSetupIntentForCardVerification(stripeCustomerId, setupIntentMetadata);

            reservation.setStripeSetupIntentId(stripeResponse.getIntentId());
            reservation.setStripePaymentIntentId(null);
            reservation.setStripeClientSecret(stripeResponse.getClientSecret());
            reservation.setStatus(ReservationStatus.PENDING_PAYMENT);

            Reservation updatedReservation = reservationRepository.save(reservation);
            if (user != null) {
                userRepository.save(user);
            }

            ReservationDTO dto = reservationMapper.toDTO(updatedReservation);
            dto.setStripeClientSecret(stripeResponse.getClientSecret());
            dto.setStripeOperationType("SETUP_INTENT");

            // Webhook will handle activation when SetupIntent succeeds
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
            stripeService.attachPaymentMethodToCustomer(stripePaymentMethodIdFromFrontend, reservation.getStripeCustomerId());
            stripeService.setDefaultPaymentMethodForCustomer(reservation.getStripeCustomerId(), stripePaymentMethodIdFromFrontend);

            reservation.setSavedPaymentMethodId(stripePaymentMethodIdFromFrontend);

            boolean statusChangedToActive = false;
            if(reservation.getStatus() == ReservationStatus.PENDING_PAYMENT && reservation.getStripeSetupIntentId() == null) {
                reservation.setStatus(ReservationStatus.ACTIVE);
                statusChangedToActive = true;
            } else if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT && reservation.getStripeSetupIntentId() != null) {
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

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT && reservation.getStatus() != ReservationStatus.PAYMENT_FAILED) {
            throw new InvalidDataException("Reservation does not have a valid status for payment initiation. Current status: " + reservation.getStatus());
        }

        if (reservation.getEndTime() == null) {
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
            return reservationMapper.toDTO(reservation);
        }

        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            // Poll for a short time
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(500); // Wait 500ms
                    reservation = reservationRepository.findById(reservationId).orElse(reservation);
                    if (reservation.getStatus() == ReservationStatus.PAID || reservation.getStatus() == ReservationStatus.ACTIVE) {
                        return reservationMapper.toDTO(reservation);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            return reservationMapper.toDTO(reservation);
        }

        throw new InvalidDataException("Reservation " + reservationId + " is in unexpected state: " + reservation.getStatus());
    }

    @Transactional
    public ReservationDTO endActivePayForUsageReservationAndInitiatePayment(String reservationId) {
        OffsetDateTime endTime = OffsetDateTime.now();

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getReservationType() != ReservationType.PAY_FOR_USAGE) {
            throw new InvalidDataException("This method is only for ending PAY_FOR_USAGE reservations.");
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidDataException("Only active PAY_FOR_USAGE reservations can be ended. Current status: " + reservation.getStatus());
        }
        if (reservation.getSavedPaymentMethodId() == null) {
            throw new IllegalStateException("No saved payment method found for this reservation. Cannot proceed with payment.");
        }
        if (reservation.getParkingLot() == null || reservation.getParkingLot().getId() == null) {
            throw new IllegalStateException("Parking lot information is missing for this reservation. Cannot calculate price.");
        }
        if (reservation.getStartTime() == null) {
            throw new IllegalStateException("Start time is missing for this reservation. Cannot calculate price.");
        }

        if (endTime.isBefore(reservation.getStartTime())) {
            throw new InvalidDataException("Calculated end time is before the reservation start time. Cannot calculate a valid price.");
        }

        Double totalAmount = calculatePrice(reservation.getParkingLot().getId(), reservation.getStartTime(), endTime);
        totalAmount = Math.max(0.0, totalAmount);

        reservation.setEndTime(endTime);
        reservation.setTotalAmount(totalAmount);

        if (totalAmount <= 0) {
            return completeZeroOrNegativePfuPayment(reservation, reservation.getUser(), reservation.getParkingLot(), totalAmount);
        }

        try {
            long amountToChargeInSmallestUnit = Math.round(totalAmount * 100);
            Map<String, String> paymentIntentMetadata = createPaymentMetadata(reservation, reservation.getUser(), reservation.getParkingLot());

            StripeIntentResponse stripeResponse = stripeService.createPaymentIntentWithSavedPaymentMethod(
                    amountToChargeInSmallestUnit,
                    "RON",
                    reservation.getStripeCustomerId(),
                    reservation.getSavedPaymentMethodId(),
                    paymentIntentMetadata
            );

            reservation.setStripePaymentIntentId(stripeResponse.getIntentId());
            reservation.setFinalAmount(totalAmount); // Final amount after calculation
            reservation.setPointsUsed(0.0); // Assuming no points used for PFU auto-ending, or this logic needs to be added
            reservation.setStatus(ReservationStatus.PENDING_PAYMENT); // Initial status before Stripe confirmation

            Reservation updatedReservation = reservationRepository.save(reservation);
            ReservationDTO dto = reservationMapper.toDTO(updatedReservation);

            if ("requires_action".equals(stripeResponse.getStatus()) || "requires_confirmation".equals(stripeResponse.getStatus())) {
                dto.setStripeClientSecret(stripeResponse.getClientSecret());
                dto.setStripeOperationType("PAYMENT_INTENT_REQUIRES_ACTION");
            } else if ("processing".equals(stripeResponse.getStatus())) {
                dto.setStripeOperationType("PAYMENT_INTENT_PROCESSING");
            } else if ("requires_payment_method".equals(stripeResponse.getStatus())) {
                dto.setStripeOperationType("PAYMENT_INTENT_REQUIRES_NEW_PAYMENT_METHOD");
            } else {
                dto.setStripeOperationType("PAYMENT_INTENT_" + stripeResponse.getStatus().toUpperCase());
            }

            return dto;
        } catch (StripeException e) {
            reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
            reservationRepository.save(reservation);
            throw new PaymentProcessingException("Payment processing failed for Pay For Usage session: " + e.getMessage());
        } catch (Exception e) {
            reservation.setStatus(ReservationStatus.PAYMENT_FAILED);
            reservationRepository.save(reservation);
            throw new RuntimeException("An unexpected error occurred while ending Pay For Usage session: " + e.getMessage(), e);
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