package com.example.licenta.DTOs;

import com.example.licenta.Enum.Reservation.ReservationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateReservationDTO {

    @NotNull(message = "Parking lot ID cannot be null")
    private Long parkingLotId;

    private Long userId;

    @NotBlank(message = "Start time cannot be blank")
    private String startTime; // ISO 8601 format

    private String endTime; // ISO 8601 format

    @NotBlank(message = "Vehicle plate cannot be blank")
    private String vehiclePlate;

    @NotBlank(message = "Phone number cannot be blank")
    private String phoneNumber;

    @Email(message = "Provide a valid email for guest reservations")
    private String guestEmail;
    private String guestName;

    @NotNull(message = "Total amount cannot be null")
    private BigDecimal totalAmount;

    private Integer pointsUsed = 0;

    @NotNull(message = "Final amount cannot be null")
    private BigDecimal finalAmount;

    @NotNull(message = "Reservation type cannot be null")
    private ReservationType reservationType = ReservationType.DIRECT;
}
