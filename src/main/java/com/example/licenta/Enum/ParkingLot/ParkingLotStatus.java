package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ParkingLotStatus {
    ACTIVE("active"),
    INACTIVE("inactive"),
    PENDING_APPROVAL("pending-approval"),
    MAINTENANCE("maintenance");

    private final String value;

    ParkingLotStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ParkingLotStatus fromString(String value) {
        for (ParkingLotStatus status : ParkingLotStatus.values()) {
            if (status.name().equalsIgnoreCase(value) || status.getValue().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new InvalidDataException("Invalid status: " + value);
    }
}