package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CustomerInsightsDTO {
    private Double averageSessionDuration; // minutes
    private Double repeatCustomerRate; // percentage
    private Double averageRating; // out of 5
    private List<PopularReservationTypeDTO> popularReservationTypes;
}