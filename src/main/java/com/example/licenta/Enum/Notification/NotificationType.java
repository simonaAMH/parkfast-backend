package com.example.licenta.Enum.Notification;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum NotificationType  {

        PARKING_SPOT_AVAILABLE("parking-spot-available"),
        RESERVATION_UPDATE("reservation-update");

        private final String value;

        NotificationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @JsonCreator
        public static com.example.licenta.Enum.Notification.NotificationType fromString(String value) {
            for (com.example.licenta.Enum.Notification.NotificationType type : com.example.licenta.Enum.Notification.NotificationType.values()) {
                if (type.name().equalsIgnoreCase(value) || type.getValue().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new InvalidDataException("Invalid parking lot type: " + value);
        }
}
