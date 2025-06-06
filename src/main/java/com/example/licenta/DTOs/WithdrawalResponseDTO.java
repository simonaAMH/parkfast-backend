package com.example.licenta.DTOs;

import com.example.licenta.Models.Withdrawal;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class WithdrawalResponseDTO {
    private String id;
    private String userId;
    private String parkingLotId;
    private String parkingLotName;
    private Double amount;
    private String bankAccountName;
    private String bankAccountNumber;
    private String stripePayoutId;
    private Withdrawal.WithdrawalStatus status;
    private String failureReason;
    private OffsetDateTime requestedAt;
    private OffsetDateTime processedAt;
}