package com.example.licenta.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenRefreshResponseDTO {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private UserDTO user;
}