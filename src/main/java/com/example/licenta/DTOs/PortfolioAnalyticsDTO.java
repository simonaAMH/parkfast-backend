package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PortfolioAnalyticsDTO {
    private PortfolioMetricsDTO portfolioMetrics;
    private List<ChartDataPointDTO<String, Double>> revenueChartData;
    private List<ChartDataPointDTO<String, Long>> reservationChartData;
    private List<ChartDataPointDTO<String, Double>> occupancyChartData;
    private List<LotPerformanceDataDTO> lotPerformanceData;
}