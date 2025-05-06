package com.example.licenta.Enum.ParkingLot;


import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum TimeUnit {
    MINUTES("minutes"),
    HOURS("hours");

    private final String value;

    TimeUnit(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TimeUnit fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (TimeUnit unit : TimeUnit.values()) {
            if (unit.name().equalsIgnoreCase(value) || unit.getValue().equalsIgnoreCase(value)) {
                return unit;
            }
        }
        throw new InvalidDataException("Invalid time unit: " + value);
    }
}