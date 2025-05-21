package com.example.licenta.Models;

import com.example.licenta.Enum.ParkingLot.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "parking_lots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParkingLot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @NotBlank
    @Size(min = 3, max = 100)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String address;

    @Column(name = "gps_coordinates")
    private String gpsCoordinates;

    @Enumerated(EnumType.STRING)
    private ParkingLotCategory category;

    @ElementCollection
    @CollectionTable(name = "parking_lot_types", joinColumns = @JoinColumn(name = "parking_lot_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private Set<ParkingLotType> types = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_vehicle_height")
    private String maxVehicleHeight;

    @Column(name = "is_lighted")
    private boolean isLighted;

    @Column(name = "has_video_surveillance")
    private boolean hasVideoSurveillance;

    @ElementCollection
    @CollectionTable(name = "parking_lot_photos", joinColumns = @JoinColumn(name = "parking_lot_id"))
    @Column(name = "photo_url")
    private List<String> photos = new ArrayList<>();

    @Column(name = "total_spots")
    private Integer totalSpots;

    @Enumerated(EnumType.STRING)
    private ParkingLotSize size;

    @Column(name = "ev_charging_spots")
    private Integer evChargingSpots;

    @Column(name = "disability_spots")
    private Integer disabilitySpots;

    @Column(name = "family_spots")
    private Integer familySpots;

    @Enumerated(EnumType.STRING)
    private AvailabilityType availability;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "parking_lot_id")
    private List<CustomHourInterval> customHourIntervals = new ArrayList<>();

    @Column(name = "allow_reservations")
    private boolean allowReservations;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_timing")
    private PaymentTiming paymentTiming;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_verification_method")
    private VerificationMethod accessVerificationMethod;

    @Column(name = "time_before_reservation")
    private Integer timeBeforeReservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_before_reservation_unit")
    private TimeUnit timeBeforeReservationUnit;

    @Column(name = "time_after_reservation")
    private Integer timeAfterReservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_after_reservation_unit")
    private TimeUnit timeAfterReservationUnit;

    @Column(name = "allow_direct_payment")
    private boolean allowDirectPayment;

    @Column(name = "require_qr_code")
    private boolean requireQrCode;

    @Column(name = "has_existing_availability_system")
    private boolean hasExistingAvailabilitySystem;

    @Column(name = "is_shared_with_non_app_users")
    private boolean isSharedWithNonAppUsers;

    @Enumerated(EnumType.STRING)
    @Column(name = "parking_availability_method")
    private AvailabilityTrackingMethod parkingAvailabilityMethod;

    @Column(name = "display_fees")
    private boolean displayFees;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type")
    private PricingType pricingType;

    @Column(name = "has_time_limits")
    private boolean hasTimeLimits;

    @Column(name = "min_parking_duration")
    private Integer minParkingDuration;

    @Column(name = "min_parking_duration_unit")
    private TimeUnit minParkingDurationUnit;

    @Column(name = "max_parking_duration")
    private Integer maxParkingDuration;

    @Column(name = "max_parking_duration_unit")
    private TimeUnit maxParkingDurationUnit;

    @Column(name = "has_free_time")
    private boolean hasFreeTime;

    @Column(name = "free_time_minutes")
    private Integer freeTimeMinutes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "parking_lot_id")
    private List<PriceInterval> priceIntervals = new ArrayList<>();

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "allow_extensions")
    private boolean allowExtensions;

    @Column(name = "max_extension_time")
    private Integer maxExtensionTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "extension_pricing_model")
    private ExtensionPricingModel extensionPricingModel;

    @Column(name = "extension_pricing_percentage")
    private Double extensionPricingPercentage;

    @Column(name = "allow_cancellations")
    private boolean allowCancellations;

    @Column(name = "allow_pre_reservation_cancellations")
    private boolean allowPreReservationCancellations;

    @Column(name = "pre_reservation_cancel_window")
    private Integer preReservationCancelWindow;

    @Column(name = "apply_pre_cancel_fee")
    private boolean applyPreCancelFee;

    @Column(name = "pre_reservation_cancel_fee")
    private Double preReservationCancelFee;

    @Column(name = "allow_mid_reservation_cancellations")
    private boolean allowMidReservationCancellations;

    @Column(name = "mid_reservation_cancel_window")
    private Integer midReservationCancelWindow;

    @Column(name = "apply_mid_cancel_fee")
    private boolean applyMidCancelFee;

    @Column(name = "mid_reservation_cancel_fee")
    private Double midReservationCancelFee;

    @Enumerated(EnumType.STRING)
    private ParkingLotStatus status = ParkingLotStatus.PENDING_APPROVAL;

    @Column(name = "spots_available")
    private Integer spotsAvailable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "average_rating")
    private Double averageRating = 0.0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void setOwner(User owner) {
        this.owner = owner;
        if (owner != null && !owner.getParkingLots().contains(this)) {
            owner.getParkingLots().add(this);
        }
    }
}