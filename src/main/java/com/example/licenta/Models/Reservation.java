package com.example.licenta.Models;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_lot_id", nullable = false)
    private ParkingLot parkingLot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "device_identifier")
    private String deviceIdentifier;

    @Column(name = "has_checked_in", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean hasCheckedIn = false;

    @Column(name = "has_checked_out", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean hasCheckedOut = false;

    @Column(name = "active_qr_token", length = 255)
    private String activeQrToken;

    @Column(name = "qr_token_expiry")
    private OffsetDateTime qrTokenExpiry;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "original_end_time")
    private OffsetDateTime originalEndTime;

    @Column(name = "extended_time_minutes")
    private Long extendedTimeMinutes = 0L;

    @NotNull
    @Column(name = "vehicle_plate", nullable = false)
    private String vehiclePlate;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "guest_name")
    private String guestName;

    @NotNull
    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Column(name = "points_used", nullable = false)
    private Double pointsUsed = 0.0;

    @NotNull
    @Column(name = "final_amount", nullable = false)
    private Double finalAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_type", nullable = false)
    private ReservationType reservationType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status = ReservationStatus.PENDING_PAYMENT;

    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Review review;

    @Column(name = "stripe_client_secret")
    private String stripeClientSecret;

    @Column(name = "saved_payment_method_id")
    private String savedPaymentMethodId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_setup_intent_id")
    private String stripeSetupIntentId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_extension_payment_intent_id")
    private String stripeExtensionPaymentIntentId;

    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    @Column(name = "refund_amount")
    private Double refundAmount;

    @Column(name = "owner_earnings_processed")
    private Boolean ownerEarningsProcessed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (originalEndTime == null && endTime != null) {
            originalEndTime = endTime;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}