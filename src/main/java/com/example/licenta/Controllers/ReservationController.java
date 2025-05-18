package com.example.licenta.Controllers;

import com.example.licenta.DTOs.ApiResponse;
import com.example.licenta.DTOs.CreateReservationDTO;
import com.example.licenta.DTOs.ReservationDTO;
import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
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

import java.math.BigDecimal;
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
    public ResponseEntity<ApiResponse<ReservationDTO>> getReservationById(@PathVariable Long id) {
        ReservationDTO reservationDTO = reservationService.getReservationById(id);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Reservation retrieved successfully", reservationDTO);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/guest-access")
    public ResponseEntity<ApiResponse<ReservationDTO>> getReservationByIdForGuest(
            @PathVariable Long id,
            @RequestParam String token) {
        ReservationDTO reservationDTO = reservationService.getReservationByIdForGuest(id, token);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(true, HttpStatus.OK.value(), "Reservation retrieved successfully", reservationDTO);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReservationsByUser(
            @PathVariable Long userId,
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

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ReservationDTO>> updateReservationStatus(
            @PathVariable Long id,
            @RequestParam ReservationStatus status,
            @RequestParam(required = false) Integer pointsUsed,
            @RequestParam(required = false) BigDecimal finalAmount
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
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> calculatePrice(
            @RequestParam Long parkingLotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {


        BigDecimal calculatedPrice = reservationService.calculatePrice(parkingLotId, startTime, endTime);

        Map<String, BigDecimal> responseData = new HashMap<>();
        responseData.put("totalAmount", calculatedPrice);

        ApiResponse<Map<String, BigDecimal>> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Price calculated successfully (mock)",
                responseData
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<ApiResponse<ReservationDTO>> getActiveReservation(@PathVariable Long userId) {
        Optional<ReservationDTO> activeReservationOpt = reservationService.findActiveReservation(userId);

        if (activeReservationOpt.isPresent()) {
            ApiResponse<ReservationDTO> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "Active reservation retrieved successfully",
                    activeReservationOpt.get()
            );
            return ResponseEntity.ok(response);
        } else {
            ApiResponse<ReservationDTO> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "No active reservations found for user",
                    null
            );
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/user/{userId}/upcoming")
    public ResponseEntity<ApiResponse<ReservationDTO>> getUpcomingReservation(@PathVariable Long userId) {
        Optional<ReservationDTO> upcomingReservationOpt = reservationService.findUpcomingReservation(userId);

        if (upcomingReservationOpt.isPresent()) {
            ApiResponse<ReservationDTO> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "Upcoming reservation retrieved successfully",
                    upcomingReservationOpt.get()
            );
            return ResponseEntity.ok(response);
        } else {
            ApiResponse<ReservationDTO> response = new ApiResponse<>(
                    true,
                    HttpStatus.OK.value(),
                    "No upcoming reservations found for user",
                    null
            );
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<ReservationDTO>> endActiveReservation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {

        String endTimeStr = (String) requestBody.get("endTime");
        BigDecimal totalAmount = new BigDecimal(requestBody.get("totalAmount").toString());

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
    @PostMapping("/{id}/payment-success")
    public ResponseEntity<ApiResponse<ReservationDTO>> handleSuccessfulPayment(@PathVariable Long id) {
        ReservationDTO updatedReservation = reservationService.handleSuccessfulPayment(id);
        ApiResponse<ReservationDTO> response = new ApiResponse<>(
                true,
                HttpStatus.OK.value(),
                "Payment processed successfully and reservation updated.",
                updatedReservation
        );
        return ResponseEntity.ok(response);
    }
}