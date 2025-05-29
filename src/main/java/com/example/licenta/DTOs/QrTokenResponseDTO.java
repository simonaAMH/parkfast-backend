package com.example.licenta.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrTokenResponseDTO {
    private String reservationId;
    private String activeQrToken;
    private OffsetDateTime qrTokenExpiry;
    private String qrCodePayload;
}