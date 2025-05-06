package com.example.licenta.Enum.User;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum Role {
    ADMIN, USER;

    @JsonCreator
    public static Role fromString(String value) {
        for (Role role : Role.values()) {
            if (role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new InvalidDataException("Invalid role: " + value);
    }
}
