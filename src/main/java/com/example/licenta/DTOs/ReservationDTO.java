package com.example.licenta.DTOs;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
public class ReservationDTO {
    private Long id;
    private Long parkingLotId;
    private String parkingLotName;
    private Long userId;
    private String username;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String vehiclePlate;
    private String phoneNumber;
    private String guestEmail;
    private String guestName;
    private BigDecimal totalAmount;
    private Integer pointsUsed;
    private BigDecimal finalAmount;
    private ReservationType reservationType;
    private ReservationStatus status;
    private String qrCodeData;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}