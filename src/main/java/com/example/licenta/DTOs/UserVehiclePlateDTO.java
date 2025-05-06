package com.example.licenta.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserVehiclePlateDTO {
    private Long id;

    @NotBlank(message = "Plate number is required")
    @Size(max = 15, message = "Plate number cannot exceed 15 characters")
    private String plateNumber;
}
