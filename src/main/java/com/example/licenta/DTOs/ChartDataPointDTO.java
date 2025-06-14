package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartDataPointDTO<L, V> {
    private L label;
    private V value;
}