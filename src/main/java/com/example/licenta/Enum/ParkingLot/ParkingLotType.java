package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ParkingLotType {
    MULTI_LEVEL("multi-level"),
    UNDERGROUND("underground"),
    ON_STREET("on-street"),
    COVERED("covered"),
    UNCOVERED("uncovered");

    private final String value;

    ParkingLotType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ParkingLotType fromString(String value) {
        for (ParkingLotType type : ParkingLotType.values()) {
            if (type.name().equalsIgnoreCase(value) || type.getValue().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new InvalidDataException("Invalid parking lot type: " + value);
    }
}
