package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentTiming {
    BEFORE("before"),
    AFTER("after"),
    BOTH("both");

    private final String value;

    PaymentTiming(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentTiming fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        for (PaymentTiming timing : PaymentTiming.values()) {
            if (timing.name().equalsIgnoreCase(value) || timing.getValue().equalsIgnoreCase(value)) {
                return timing;
            }
        }
        throw new InvalidDataException("Invalid payment timing: " + value);
    }
}