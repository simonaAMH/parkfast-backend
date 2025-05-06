package com.example.licenta.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponseDTO {
    private String accessToken;
    private UserDTO user;
    private String tokenType;
    private String refreshToken;

    public LoginResponseDTO(String accessToken, UserDTO user, String tokenType) {
        this.accessToken = accessToken;
        this.user = user;
        this.tokenType = tokenType;
        this.refreshToken = null;
    }
}