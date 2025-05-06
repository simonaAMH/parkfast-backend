package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum PricingType {
    FIXED("Fixed"),
    DYNAMIC("Dynamic");

    private final String value;

    PricingType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PricingType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (PricingType type : PricingType.values()) {
            if (type.name().equalsIgnoreCase(value) || type.getValue().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new InvalidDataException("Invalid pricing type: " + value);
    }
}
