package com.example.licenta.Enum.Reservation;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ReservationStatus {
    PENDING_PAYMENT("pending_payment"),
    PAID("paid"),
    ACTIVE("active"),
    PAYMENT_FAILED("payment_failed"),
    CANCELLED("cancelled");

    private final String value;

    ReservationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ReservationStatus fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (ReservationStatus method : ReservationStatus.values()) {
            if (method.name().equalsIgnoreCase(value) || method.getValue().equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new InvalidDataException("Invalid reservation status: " + value);
    }
}