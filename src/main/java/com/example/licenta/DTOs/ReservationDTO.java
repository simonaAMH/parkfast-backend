package com.example.licenta.DTOs;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ReservationDTO {
    private String id;
    private String parkingLotId;
    private String parkingLotName;
    private String userId;
    private String username;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String vehiclePlate;
    private String phoneNumber;
    private String guestEmail;
    private String guestName;
    private Double totalAmount;
    private Integer pointsUsed;
    private Double finalAmount;
    private ReservationType reservationType;
    private ReservationStatus status;
    private String qrCodeData;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}