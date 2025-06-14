package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeriodAnalyticsDTO {
    private Double revenue;
    private Long reservations;
    private Double averageRevenue; // revenue / reservations
    private Double occupancyRate; // percentage
    private Double cancellationRate; // percentage
    private Double revenueGrowth; // percentage change
    private Double reservationGrowth; // percentage change
    // Add occupancyGrowth if needed, similar to portfolio
}