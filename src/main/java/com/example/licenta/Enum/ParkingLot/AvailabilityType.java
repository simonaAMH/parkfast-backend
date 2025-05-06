package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum AvailabilityType {
    ALL_DAY("24/7"),
    WORKING_HOURS("Working"),
    CUSTOM("Custom");

    private final String value;

    AvailabilityType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AvailabilityType fromString(String value) {
        for (AvailabilityType type : AvailabilityType.values()) {
            if (type.name().equalsIgnoreCase(value) || type.getValue().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new InvalidDataException("Invalid availability type: " + value);
    }
}