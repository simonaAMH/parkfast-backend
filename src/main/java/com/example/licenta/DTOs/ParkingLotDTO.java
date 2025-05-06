package com.example.licenta.DTOs;

import com.example.licenta.Enum.ParkingLot.*;
import com.example.licenta.Validators.ParkingLotConstraint;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import lombok.Data;

@Data
@ParkingLotConstraint
public class ParkingLotDTO {
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address must be less than 255 characters")
    private String address;

    @Pattern(regexp = "^(-?\\d+(\\.\\d+)?),(-?\\d+(\\.\\d+)?)$",
            message = "GPS coordinates must be in format 'latitude,longitude'")
    private String gpsCoordinates;

    @NotNull(message = "Category is required")
    @Enumerated(EnumType.STRING)
    private ParkingLotCategory category;

    @NotNull(message = "Owner ID is required")
    private Long ownerId;

    @NotEmpty(message = "At least one parking lot type is required")
    @Enumerated(EnumType.STRING)
    private Set<ParkingLotType> types;

    @Size(max = 1000, message = "Description must be less than 1000 characters")
    private String description;

    @Pattern(regexp = "^\\d+(\\.\\d+)?$", message = "Max vehicle height must be a numeric value")
    @Min(value = 1, message = "Maximum vehicle height must be at least 1")
    private String maxVehicleHeight;

    @JsonProperty("isLighted")
    private boolean isLighted;

    private boolean hasVideoSurveillance;

    private List<String> photos;

    @NotNull(message = "Total spots is required")
    private Integer totalSpots;

    @Enumerated(EnumType.STRING)
    private ParkingLotSize size;

    private Integer evChargingSpots;

    private Integer disabilitySpots;

    private Integer familySpots;

    @Enumerated(EnumType.STRING)
    private AvailabilityType availability;

    private boolean allowReservations;

    @Enumerated(EnumType.STRING)
    private PaymentTiming paymentTiming;

    @Enumerated(EnumType.STRING)
    private VerificationMethod accessVerificationMethod;

    private Integer timeBeforeReservation;

    @Enumerated(EnumType.STRING)
    private TimeUnit timeBeforeReservationUnit;

    private Integer timeAfterReservation;

    @Enumerated(EnumType.STRING)
    private TimeUnit timeAfterReservationUnit;

    private boolean allowDirectPayment;
    private boolean requireQrCode;
    private boolean hasExistingAvailabilitySystem;
    private boolean isSharedWithNonAppUsers;

    @Enumerated(EnumType.STRING)
    private AvailabilityTrackingMethod parkingAvailabilityMethod;

    private boolean displayFees;

    @Enumerated(EnumType.STRING)
    private PricingType pricingType;

    private boolean hasTimeLimits;
    private Integer minParkingDuration;
    @Enumerated(EnumType.STRING)
    private TimeUnit minParkingDurationUnit;
    private Integer maxParkingDuration;
    @Enumerated(EnumType.STRING)
    private TimeUnit maxParkingDurationUnit;

    private boolean hasFreeTime;
    private Integer freeTimeMinutes;

    @Size(max = 100, message = "Bank account name must be less than 100 characters")
    private String bankAccountName;

    private String bankAccountNumber;

    private boolean allowExtensions;
    private Integer maxExtensionTime;

    @Enumerated(EnumType.STRING)
    private ExtensionPricingModel extensionPricingModel;
    private Double extensionPricingPercentage;

    private boolean allowCancellations;
    private boolean allowPreReservationCancellations;
    private Integer preReservationCancelWindow;
    private boolean applyPreCancelFee;
    private Double preReservationCancelFee;

    private boolean allowMidReservationCancellations;
    private Integer midReservationCancelWindow;
    private boolean applyMidCancelFee;
    private Double midReservationCancelFee;

    @Enumerated(EnumType.STRING)
    private ParkingLotStatus status;

    private Integer spotsAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Valid
    private List<@Valid CustomHourIntervalDTO> customHourIntervals;

    @Valid
    private List<@Valid PriceIntervalDTO> priceIntervals;
}