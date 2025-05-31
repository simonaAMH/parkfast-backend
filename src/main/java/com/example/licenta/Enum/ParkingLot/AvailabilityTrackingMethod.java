package com.example.licenta.Enum.ParkingLot;
import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum AvailabilityTrackingMethod {
    CAMERA_AI("camera_ai"),
    HISTORICAL_DATA("historical_data"),
    SENSORS("sensors");

    private final String value;

    AvailabilityTrackingMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AvailabilityTrackingMethod fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (AvailabilityTrackingMethod method : AvailabilityTrackingMethod.values()) {
            if (method.name().equalsIgnoreCase(value) || method.getValue().equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new InvalidDataException("Invalid availability tracking method: " + value);
    }
}