package com.example.licenta.Enum.ParkingLot;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ExtensionPricingModel {
    SAME("same"),
    HIGHER("higher");

    private final String value;

    ExtensionPricingModel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ExtensionPricingModel fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (ExtensionPricingModel model : ExtensionPricingModel.values()) {
            if (model.name().equalsIgnoreCase(value) || model.getValue().equalsIgnoreCase(value)) {
                return model;
            }
        }
        throw new InvalidDataException("Invalid extension pricing model: " + value);
    }
}