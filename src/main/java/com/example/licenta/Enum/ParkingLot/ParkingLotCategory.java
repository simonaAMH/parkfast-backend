package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ParkingLotCategory {
    PUBLIC("public"),
    PRIVATE("private");

    private final String value;

    ParkingLotCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ParkingLotCategory fromString(String value) {
        for (ParkingLotCategory category : ParkingLotCategory.values()) {
            if (category.name().equalsIgnoreCase(value) || category.getValue().equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new InvalidDataException("Invalid category: " + value);
    }
}