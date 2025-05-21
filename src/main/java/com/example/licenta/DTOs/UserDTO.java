package com.example.licenta.DTOs;

import com.example.licenta.Enum.User.Role;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class UserDTO {

    private String id;
    private String email;
    private String username;
    private String profileImage;
    private String phoneNumber;
    private BigDecimal pendingEarnings;
    private BigDecimal totalEarnings;
    private BigDecimal paidEarnings;
    private Role role;
    private Integer loyaltyPoints;
    private boolean emailVerified;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}