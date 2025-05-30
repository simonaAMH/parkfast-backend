package com.example.licenta.Controllers;

import com.example.licenta.DTOs.*;
import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Services.ReservationService;
import jakarta.validation.Valid;
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
            @RequestParam ReservationStatus status,
            @RequestParam(required = false) Double pointsUsed,
            @RequestParam(required = false) Double finalAmount
    ) {

        ReservationDTO updatedReservation = reservationService.updateReservationStatus(id, status, pointsUsed, finalAmount);

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

    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<ReservationDTO>> endActiveReservation(
            @PathVariable String id,
            @RequestBody Map<String, Object> requestBody) {

        String endTimeStr = (String) requestBody.get("endTime");
        Double totalAmount = Double.parseDouble(requestBody.get("totalAmount").toString());

        OffsetDateTime endTime;
        try {
            endTime = OffsetDateTime.parse(endTimeStr);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, HttpStatus.BAD_REQUEST.value(),
                            "Invalid date format for endTime", null));
        }

        ReservationDTO updatedReservation = reservationService.endActiveReservation(id, endTime, totalAmount);

        ApiResponse<ReservationDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Reservation ended successfully",
                updatedReservation
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/payment")
    public ResponseEntity<ApiResponse<ReservationDTO>> handlePayment(
            @PathVariable String id,
            @Valid @RequestBody PaymentRequestDTO paymentRequest) {

        ReservationDTO updatedReservation = reservationService.handlePayment(id, paymentRequest);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Payment processed successfully and reservation updated.",
                updatedReservation
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
}