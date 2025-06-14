package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PopularReservationTypeDTO {
    private String type; // e.g., "STANDARD", "PAY_FOR_USAGE"
    private Long count;
    private Double percentage;
}