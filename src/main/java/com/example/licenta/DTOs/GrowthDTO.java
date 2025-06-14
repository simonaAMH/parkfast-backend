package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrowthDTO {
    private Double revenue; // percentage change
    private Double reservations; // percentage change
    private Double occupancy; // percentage change
}