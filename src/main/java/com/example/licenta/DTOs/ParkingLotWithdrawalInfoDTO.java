package com.example.licenta.DTOs;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkingLotWithdrawalInfoDTO {
    private String id;
    private String name;
    private String address;
    private String bankAccountName;
    private String bankAccountNumber;
    private Double pendingEarnings;
    private boolean canWithdraw;
    private String withdrawalBlockReason;
}