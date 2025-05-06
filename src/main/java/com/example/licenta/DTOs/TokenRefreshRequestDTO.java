package com.example.licenta.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshRequestDTO {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}