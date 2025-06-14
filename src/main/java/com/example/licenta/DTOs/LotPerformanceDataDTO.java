package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LotPerformanceDataDTO {
    private String label; // Lot name
    private Double value; // e.g., total revenue for this lot in the period
}