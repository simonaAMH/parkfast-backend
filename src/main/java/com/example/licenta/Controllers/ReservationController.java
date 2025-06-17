package com.example.licenta.Controllers;

import com.example.licenta.DTOs.*;
import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.JwtComponents.JwtAuthenticationFilter;
import com.example.licenta.Models.Reservation;
import com.example.licenta.Services.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationDTO>> createDirectReservation(
            @Valid @RequestBody CreateReservationDTO createReservationDTO) {
        ReservationDTO createdReservation = reservationService.createDirectReservation(createReservationDTO);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.CREATED.value(), "Direct reservation initiated successfully. Proceed to payment.", createdReservation);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationDTO>> getReservationById(@PathVariable String id) {
        ReservationDTO reservationDTO = reservationService.getReservationById(id);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Reservation retrieved successfully", reservationDTO);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/guest-access")
    public ResponseEntity<ApiResponse<ReservationDTO>> getReservationByIdForGuest(
            @PathVariable String id,
            @RequestParam String token) {
        ReservationDTO reservationDTO = reservationService.getReservationByIdForGuest(id, token);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Reservation retrieved successfully", reservationDTO);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReservationsByUser(
            @PathVariable String userId,
            @RequestParam(required = false) List<ReservationType> types,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        logger.info("Raw 'types' parameter received: {}", types);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<ReservationDTO> reservationPage = reservationService.getReservationsByUserId(userId, types, pageable);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("reservations", reservationPage.getContent());
        responseData.put("currentPage", reservationPage.getNumber());
        responseData.put("totalItems", reservationPage.getTotalElements());
        responseData.put("totalPages", reservationPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "User reservations retrieved successfully",
                responseData
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/parking-lot/{parkingLotId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReservationsByParkingLot(
            @PathVariable String parkingLotId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Sort sort;
        if ("ENDED".equalsIgnoreCase(period)) {
            sort = Sort.by(Sort.Order.desc("endTime"));
        } else {
            sort = Sort.by(Sort.Order.desc("startTime"));
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ReservationDTO> reservationPage = reservationService.getReservationsForParkingLotByPeriod(parkingLotId, period, pageable);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("reservations", reservationPage.getContent());
        responseData.put("currentPage", reservationPage.getNumber());
        responseData.put("totalItems", reservationPage.getTotalElements());
        responseData.put("totalPages", reservationPage.getTotalPages());

        ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Parking lot reservations retrieved successfully",
                responseData
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ReservationDTO>> updateReservationStatus(
            @PathVariable String id,
            @RequestParam ReservationStatus status
    ) {

        ReservationDTO updatedReservation = reservationService.updateReservationStatus(id, status);

        ApiResponse<ReservationDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Reservation status and details updated successfully",
                updatedReservation
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/calculate-price")
    public ResponseEntity<ApiResponse<Map<String, Double>>> calculatePrice(
            @RequestParam String parkingLotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {


        Double calculatedPrice = reservationService.calculatePrice(parkingLotId, startTime, endTime);

        Map<String, Double> responseData = new HashMap<>();
        responseData.put("totalAmount", calculatedPrice);

        ApiResponse<Map<String, Double>> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Price calculated successfully (mock)",
                responseData
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<ApiResponse<ReservationDTO>> getActiveReservation(@PathVariable String userId) {
        Optional<ReservationDTO> activeReservationOpt = reservationService.findActiveReservation(userId);

        ApiResponse<ReservationDTO> response;
        response = activeReservationOpt.map(reservationDTO -> new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Active reservation retrieved successfully",
                reservationDTO
        )).orElseGet(() -> new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "No active reservations found for user",
                null
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/upcoming")
    public ResponseEntity<ApiResponse<ReservationDTO>> getUpcomingReservation(@PathVariable String userId) {
        Optional<ReservationDTO> upcomingReservationOpt = reservationService.findUpcomingReservation(userId);

        ApiResponse<ReservationDTO> response;
        response = upcomingReservationOpt.map(reservationDTO -> new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Upcoming reservation retrieved successfully",
                reservationDTO
        )).orElseGet(() -> new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "No upcoming reservations found for user",
                null
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/parking-lot/{parkingLotId}/relevant-reservations")
    public ResponseEntity<ApiResponse<List<ReservationDTO>>> findActiveOrUpcomingReservationsForLot(
            @PathVariable String userId,
            @PathVariable String parkingLotId,
            @RequestParam(defaultValue = "1") int upcomingWindowHours) {

        List<ReservationDTO> relevantReservations = reservationService.findActiveOrUpcomingReservationsForLot(userId, parkingLotId, upcomingWindowHours);

        ApiResponse<List<ReservationDTO>> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                relevantReservations.isEmpty() ? "No active or upcoming reservations found for the specified lot." : "Successfully retrieved active or upcoming reservations for the specified lot.",
                relevantReservations
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/reviews")
    public ResponseEntity<ApiResponse<ReviewDTO>> createReviewForReservation(
            @PathVariable String reservationId,
            @Valid @RequestBody CreateReviewDTO createReviewDto) {
        ReviewDTO createdReview = reservationService.createReview(reservationId, createReviewDto);
        ApiResponse<ReviewDTO> response = new ApiResponse<>(
                true, HttpStatus.CREATED.value(), "Review submitted successfully", createdReview);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{reservationId}/reviews")
    public ResponseEntity<ApiResponse<ReviewDTO>> getReviewForReservation(@PathVariable String reservationId) {
        ReviewDTO reviewDTO = reservationService.getReviewByReservationId(reservationId);
        ApiResponse<ReviewDTO> response = new ApiResponse<>(
                true, HttpStatus.OK.value(), "Review retrieved successfully", reviewDTO);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview( @PathVariable String reviewId) {
        reservationService.deleteReview(reviewId);
        ApiResponse<Void> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Review deleted successfully", null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/generate-qr-token")
    public ResponseEntity<ApiResponse<QrTokenResponseDTO>> generateQrTokenForReservation(@PathVariable String reservationId) {
        QrTokenResponseDTO qrTokenResponse = reservationService.generateActiveQrToken(reservationId);
        ApiResponse<QrTokenResponseDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "QR token generated successfully.",
                qrTokenResponse
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm-client-payment")
    public ResponseEntity<ApiResponse<ReservationDTO>> confirmClientStripePaymentSuccess(@PathVariable String id) {
        ReservationDTO updatedReservation = reservationService.confirmClientStripePaymentSuccess(id);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Client payment confirmation processed. Reservation updated.",
                updatedReservation
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/activate-pay-for-usage")
    public ResponseEntity<ApiResponse<ReservationDTO>> activatePayForUsage(@PathVariable String reservationId) {
        ReservationDTO reservationDTO = reservationService.activatePayForUsageReservation(reservationId);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Pay for Usage activation initiated. Proceed with card setup.", reservationDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/save-payment-method")
    public ResponseEntity<ApiResponse<ReservationDTO>> savePayForUsagePaymentMethod(
            @PathVariable String reservationId,
            @RequestBody Map<String, String> payload) {
        String stripePaymentMethodId = payload.get("stripePaymentMethodId");
        if (stripePaymentMethodId == null || stripePaymentMethodId.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, HttpStatus.BAD_REQUEST.value(), "stripePaymentMethodId is required.", null));
        }
        ReservationDTO reservationDTO = reservationService.savePayForUsagePaymentMethod(reservationId, stripePaymentMethodId);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Payment method saved and Pay for Usage activated.", reservationDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/process-standard-direct-payment")
    public ResponseEntity<ApiResponse<ReservationDTO>> processStandardOrDirectPayment(
            @PathVariable String reservationId,
            @Valid @RequestBody(required = false) PaymentRequestDTO paymentRequest) {
        ReservationDTO reservationDTO = reservationService.processStandardOrDirectPayment(reservationId, paymentRequest != null ? paymentRequest : new PaymentRequestDTO());
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Payment initiated for Standard/Direct reservation. Confirm with Stripe.", reservationDTO);
        return ResponseEntity.ok(response);
    }

    public static class EndPayForUsageRequest {
        @NotNull
        @Getter
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private OffsetDateTime endTime;

        @Getter
        @NotNull
        private Double totalAmount;
    }

    @PostMapping("/{reservationId}/end-pay-for-usage")
    public ResponseEntity<ApiResponse<ReservationDTO>> endPayForUsageAndInitiatePayment(
            @PathVariable String reservationId) {
        ReservationDTO reservationDTO = reservationService.endActivePayForUsageReservationAndInitiatePayment(reservationId);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Pay for Usage session ended. Payment initiated.", reservationDTO);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{reservationId}/extend")
    public ResponseEntity<ApiResponse<ReservationDTO>> extendReservation(
            @PathVariable String reservationId,
            @RequestBody @Valid ExtendReservationRequest request) {

        ReservationDTO extendedReservation = reservationService.extendReservation(reservationId, request.getNewEndTime());
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Extention initiated.", extendedReservation);
        return ResponseEntity.ok(response);
    }

    public static class ExtendReservationRequest {
        @NotNull
        @Getter
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private OffsetDateTime newEndTime;
    }

    @PutMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<ReservationDTO>>cancelReservation(@PathVariable String reservationId) {
        ReservationDTO cancelledReservation = reservationService.cancelReservation(reservationId);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Cancellation initiated.", cancelledReservation);
        return ResponseEntity.ok(response);
    }

}