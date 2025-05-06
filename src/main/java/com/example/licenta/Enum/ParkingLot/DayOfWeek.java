package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    @JsonCreator
    public static DayOfWeek fromString(String value) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().equalsIgnoreCase(value)) {
                return day;
            }
        }
        throw new InvalidDataException("Invalid day of week: " + value);
    }
}