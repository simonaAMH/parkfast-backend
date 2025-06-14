package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioMetricsDTO {
    private Double totalRevenue;
    private Long totalReservations;
    private Double averageOccupancy; // percentage
    private String topPerformingLot;
    private GrowthDTO growth;
}