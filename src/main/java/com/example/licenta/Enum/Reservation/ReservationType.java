package com.example.licenta.Enum.Reservation;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ReservationType {
    DIRECT("direct"),
    STANDARD("standard"),    // reserve and pay before the arrival
    PAY_FOR_USAGE("pay_for_usage"); // reserve before the arrival, pay before leaving

    private final String value;

    ReservationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ReservationType fromString(String value) {
        if (value == null) {
            throw new InvalidDataException("Invalid reservation type: null");
        }
        String trimmedValue = value.trim();
        for (ReservationType type : ReservationType.values()) {
            if (type.name().equalsIgnoreCase(trimmedValue) || type.getValue().equalsIgnoreCase(trimmedValue)) {
                return type;
            }
        }
        throw new InvalidDataException("Invalid reservation type: " + value);
    }
}