package com.example.licenta.Validators;

import com.example.licenta.DTOs.ParkingLotDTO;
import com.example.licenta.Enum.ParkingLot.AvailabilityType;
import com.example.licenta.Enum.ParkingLot.ExtensionPricingModel;
import com.example.licenta.Enum.ParkingLot.PaymentTiming;
import com.example.licenta.Enum.ParkingLot.PricingType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

public class ParkingLotValidator implements ConstraintValidator<ParkingLotConstraint, ParkingLotDTO> {

    @Override
    public void initialize(ParkingLotConstraint constraintAnnotation) {
    }

    @Override
    public boolean isValid(ParkingLotDTO parkingLot, ConstraintValidatorContext context) {
        List<String> validationErrors = new ArrayList<>();

        context.disableDefaultConstraintViolation();

        checkFieldNotZero(parkingLot.getTotalSpots(), "Total spots", validationErrors);
        if (parkingLot.getMaxVehicleHeight() != null && !parkingLot.getMaxVehicleHeight().isEmpty()) {
            checkStringFieldNotZero(parkingLot.getMaxVehicleHeight(), "Maximum vehicle height", validationErrors);
        }

        // 1. Extensions validation
        if (parkingLot.isAllowReservations()) {
            if (parkingLot.isAllowExtensionsForRegular()) {
                if (parkingLot.getMaxExtensionTimeForRegular() == null) {
                    validationErrors.add("Max extension time is required when extensions are allowed");
                } else {
                    checkFieldNotZero(parkingLot.getMaxExtensionTimeForRegular(), "Max extension time", validationErrors);
                }

                if (parkingLot.getExtensionPricingModelForRegular() == null) {
                    validationErrors.add("Extension pricing model is required when extensions are allowed");
                } else if (parkingLot.getExtensionPricingModelForRegular() == ExtensionPricingModel.HIGHER) {
                    if (parkingLot.getExtensionPricingPercentageForRegular() == null) {
                        validationErrors.add("Extension pricing percentage is required for HIGHER pricing model");
                    } else {
                        checkFieldNotZero(parkingLot.getExtensionPricingPercentageForRegular(), "Extension pricing percentage", validationErrors);
                    }
                }
            }

            // 2. Payment timing validation
            if (parkingLot.getTimeBeforeReservation() == null) {
                validationErrors.add("Time before reservation is required");
            } else {
                checkFieldNotZero(parkingLot.getTimeBeforeReservation(), "Time before reservation", validationErrors);
            }
            if (parkingLot.getTimeBeforeReservationUnit() == null) {
                validationErrors.add("Time before reservation unit is required");
            }


            if (parkingLot.getTimeAfterReservation() == null) {
                validationErrors.add("Time after reservation is required");
            } else {
                checkFieldNotZero(parkingLot.getTimeAfterReservation(), "Time after reservation", validationErrors);
            }
            if (parkingLot.getTimeAfterReservationUnit() == null) {
                validationErrors.add("Time after reservation unit is required");
            }


            // 3. Availability tracking validation
            if (!parkingLot.isHasExistingAvailabilitySystem()) {
                if (parkingLot.isSharedWithNonAppUsers() && parkingLot.getParkingAvailabilityMethod() == null) {
                    validationErrors.add("Parking availability method is required when shared with non-app users");
                }
            }

            // 5. Time limits validation
            if (parkingLot.isHasTimeLimits()) {
                if (parkingLot.getMaxParkingDuration() == null || parkingLot.getMinParkingDuration() == null) {
                    validationErrors.add("Min and Max parking duration are required when time limits are enabled");
                } else if (parkingLot.getMaxParkingDurationUnit() == null || parkingLot.getMinParkingDurationUnit() == null) {
                    validationErrors.add("Min and Max parking time units are required when time limits are enabled");
                } else {
                    checkFieldNotZero(parkingLot.getMaxParkingDuration(), "Max parking duration", validationErrors);
                }
            }

            // Check min parking duration if provided
            if (parkingLot.getMinParkingDuration() != null) {
                checkFieldNotZero(parkingLot.getMinParkingDuration(), "Min parking duration", validationErrors);
            }

            // 6. Free time validation
            if (parkingLot.isHasFreeTime()) {
                if (parkingLot.getFreeTimeMinutes() == null) {
                    validationErrors.add("Free time minutes is required when free time is enabled");
                } else {
                    checkFieldNotZero(parkingLot.getFreeTimeMinutes(), "Free time minutes", validationErrors);
                }
            }

            // 7. Cancellation validation
            if (parkingLot.isAllowCancellations()) {
                if (parkingLot.isAllowPreReservationCancellations()) {
                    if (parkingLot.getPreReservationCancelWindow() == null) {
                        validationErrors.add("Pre-reservation cancel window is required when pre-reservation cancellations are allowed");
                    } else {
                        checkFieldNotZero(parkingLot.getPreReservationCancelWindow(), "Pre-reservation cancel window", validationErrors);
                    }

                    if (parkingLot.isApplyPreCancelFee()) {
                        if (parkingLot.getPreReservationCancelFee() == null) {
                            validationErrors.add("Pre-reservation cancel fee is required when pre-cancel fees are applied");
                        } else {
                            checkFieldNotZero(parkingLot.getPreReservationCancelFee(), "Pre-reservation cancel fee", validationErrors);
                        }
                    }
                }

                if (parkingLot.isAllowMidReservationCancellations()) {
                    if (parkingLot.getMidReservationCancelWindow() == null) {
                        validationErrors.add("Mid-reservation cancel window is required when mid-reservation cancellations are allowed");
                    } else {
                        checkFieldNotZero(parkingLot.getMidReservationCancelWindow(), "Mid-reservation cancel window", validationErrors);
                    }

                    if (parkingLot.isApplyMidCancelFee()) {
                        if (parkingLot.getMidReservationCancelFee() == null) {
                            validationErrors.add("Mid-reservation cancel fee is required when mid-cancel fees are applied");
                        } else {
                            checkFieldNotZero(parkingLot.getMidReservationCancelFee(), "Mid-reservation cancel fee", validationErrors);
                        }
                    }
                }
            }
        }

        if (parkingLot.isAllowDirectPayment()) {
            if (parkingLot.isAllowExtensionsForOnTheSpot()) {
                if (parkingLot.getMaxExtensionTimeForOnTheSpot() == null) {
                    validationErrors.add("Max extension time is required when extensions are allowed");
                } else {
                    checkFieldNotZero(parkingLot.getMaxExtensionTimeForOnTheSpot(), "Max extension time", validationErrors);
                }

                if (parkingLot.getExtensionPricingModelForOnTheSpot() == null) {
                    validationErrors.add("Extension pricing model is required when extensions are allowed");
                } else if (parkingLot.getExtensionPricingModelForOnTheSpot() == ExtensionPricingModel.HIGHER) {
                    if (parkingLot.getExtensionPricingPercentageForOnTheSpot() == null) {
                        validationErrors.add("Extension pricing percentage is required for HIGHER pricing model");
                    } else {
                        checkFieldNotZero(parkingLot.getExtensionPricingPercentageForOnTheSpot(), "Extension pricing percentage", validationErrors);
                    }
                }
            }
        }

            if (parkingLot.isAllowReservations() || parkingLot.isDisplayFees() || parkingLot.isAllowDirectPayment()) {
            // 4. Pricing type validation
            if (parkingLot.getPricingType() != null && parkingLot.getPricingType() == PricingType.FIXED) {
                if (parkingLot.getPriceIntervals() == null || parkingLot.getPriceIntervals().isEmpty()) {
                    validationErrors.add("Price intervals are required for DYNAMIC pricing type");
                } else {
                    // Check that all price intervals have valid values
                    parkingLot.getPriceIntervals().forEach(interval -> {
                        if (interval.getPrice() == null || interval.getPrice() <= 0) {
                            validationErrors.add("Price interval price must be greater than zero");
                        }
                        if (interval.getDuration() == null || interval.getDuration() <= 0) {
                            validationErrors.add("Price interval duration must be greater than zero");
                        }
                    });
                }
            }
        }

        // 8. Availability custom hours validation
        if (parkingLot.getAvailability() == AvailabilityType.CUSTOM &&
                (parkingLot.getCustomHourIntervals() == null || parkingLot.getCustomHourIntervals().isEmpty())) {
            validationErrors.add("Custom hour intervals are required when availability is set to CUSTOM");
        }

        // Add validation errors to context
        for (String errorMessage : validationErrors) {
            context.buildConstraintViolationWithTemplate(errorMessage)
                    .addConstraintViolation();
        }

        return validationErrors.isEmpty();
    }

    private void checkStringFieldNotZero(String value, String fieldName, List<String> errors) {
        try {
            if (value != null && !value.isEmpty()) {
                double numValue = Double.parseDouble(value);
                if (numValue <= 0) {
                    errors.add(fieldName + " must be greater than zero");
                }
            }
        } catch (NumberFormatException e) {
            // Ignore parsing errors as they will be caught by other validators
        }
    }

    private void checkFieldNotZero(Number value, String fieldName, List<String> errors) {
        if (value != null && value.doubleValue() <= 0) {
            errors.add(fieldName + " must be greater than zero");
        }
    }
}