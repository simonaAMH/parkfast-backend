package com.example.licenta.DTOs;

import com.example.licenta.Enum.ParkingLot.DayOfWeek;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CustomHourIntervalDTO {
    private String id;

    @NotBlank(message = "Start time is required")
    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
            message = "Start time must be in format HH:MM (24-hour)")
    private String startTime;

    @NotBlank(message = "End time is required")
    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
            message = "End time must be in format HH:MM (24-hour)")
    private String endTime;

    @NotEmpty(message = "At least one day must be selected")
    @Enumerated(EnumType.STRING)
    private List<DayOfWeek> days;
}