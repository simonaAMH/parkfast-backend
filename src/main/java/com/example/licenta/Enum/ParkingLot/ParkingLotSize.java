package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ParkingLotSize {
    COMPACT("compact"),
    STANDARD("standard"),
    LARGE("large");

    private final String value;

    ParkingLotSize(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ParkingLotSize fromString(String value) {
        for (ParkingLotSize size : ParkingLotSize.values()) {
            if (size.name().equalsIgnoreCase(value) || size.getValue().equalsIgnoreCase(value)) {
                return size;
            }
        }
        throw new InvalidDataException("Invalid size: " + value);
    }
}