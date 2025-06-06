package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WithdrawalSummaryDTO {
    private Double totalPendingEarnings;
    private Double totalPaidEarnings;
    private Integer totalWithdrawals;
    private List<WithdrawalResponseDTO> recentWithdrawals;
    private List<ParkingLotWithdrawalInfoDTO> availableParkingLots;
}