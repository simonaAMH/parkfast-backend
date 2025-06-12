package com.example.licenta.DTOs;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;

public class PaymentRequestDTO {

    @Getter
    @DecimalMin(value = "0.0", message = "Points to use must be non-negative")
    private Double pointsToUse;


    @Getter
    private String setupFutureUsage;

}