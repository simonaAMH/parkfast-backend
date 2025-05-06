package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum VerificationMethod {
    BARRIER_INTEGRATION("barrier_integration"),
    GPS_CHECK("gps_check"),
    QR_SCAN("qr_scan");

    private final String value;

    VerificationMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VerificationMethod fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (VerificationMethod method : VerificationMethod.values()) {
            if (method.name().equalsIgnoreCase(value) || method.getValue().equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new InvalidDataException("Invalid verification method: " + value);
    }
}