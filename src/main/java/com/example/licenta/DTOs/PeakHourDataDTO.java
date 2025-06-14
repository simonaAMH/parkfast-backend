package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeakHourDataDTO {
    private Integer hour; // 0-23
    private Long reservations;
    private Double revenue;
    private Double occupancyRate; // percentage for that hour
}