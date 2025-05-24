package com.example.licenta.Enum.Notification;

import com.example.licenta.Exceptions.InvalidDataException;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum NotificationType  {

        AVAILABLE_SPOTS_NEARBY("available-spots-nearby"),
        RESERVATION_UPDATE("reservation-update"),
        PROMPT_START_RESERVATION("prompt-start-reservation"),
        PROMPT_END_RESERVATION("prompt-end-reservation"),
        CONTRIBUTE_PARKING_LOT_INFO("contribute-parking-lot-info"),
        REVIEW_REMINDER("review-reminder"),
        GENERAL_INFO("general-info");

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
