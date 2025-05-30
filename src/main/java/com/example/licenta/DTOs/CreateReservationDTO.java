package com.example.licenta.DTOs;

import com.example.licenta.Enum.Reservation.ReservationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReservationDTO {

    @NotNull(message = "Parking lot ID cannot be null")
    private String parkingLotId;

    private String userId;

    private String deviceIdentifier;

    @NotBlank(message = "Start time cannot be blank")
    private String startTime;

    private String endTime;

    @NotBlank(message = "Vehicle plate cannot be blank")
    private String vehiclePlate;

    @NotBlank(message = "Phone number cannot be blank")
    private String phoneNumber;

    @Email(message = "Provide a valid email for guest reservations")
    private String guestEmail;
    private String guestName;

    @NotNull(message = "Total amount cannot be null")
    private Double totalAmount;

    private Double pointsUsed = 0.0;

    @NotNull(message = "Final amount cannot be null")
    private Double finalAmount;

    @NotNull(message = "Reservation type cannot be null")
    private ReservationType reservationType = ReservationType.DIRECT;
}
