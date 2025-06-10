package com.example.licenta.DTOs;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Models.Review;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ReservationDTO {
    private String id;
    private String parkingLotId;
    private String userId;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String vehiclePlate;
    private String phoneNumber;
    private String guestEmail;
    private String guestName;
    private Double totalAmount;
    private Double pointsUsed;
    private Double finalAmount;
    private ReservationType reservationType;
    private ReservationStatus status;
    private String reviewId;
    private String deviceIdentifier;
    private boolean hasCheckedIn;
    private boolean hasCheckedOut;
    private String activeQrToken;
    private OffsetDateTime qrTokenExpiry;

    private String stripeClientSecret;
    private String stripeOperationType; // e.g., "SETUP_INTENT", "PAYMENT_INTENT", "PAYMENT_INTENT_REQUIRES_ACTION"
    private String stripeCustomerId;
    private String stripePaymentMethodId;
    private String stripeSetupIntentId;
    private String stripePaymentIntentId;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}