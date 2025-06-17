package com.example.licenta.DTOs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAvailabilityRequestDTO {

    @NotNull(message = "Number of empty spaces is required.")
    @Min(value = 0, message = "Number of empty spaces cannot be negative.")
    private Integer emptySpaces;
}