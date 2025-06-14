package com.example.licenta.DTOs;

import com.example.licenta.Enum.User.Role;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserUpdateDTO {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "Username can only contain letters, numbers and underscores")
    private String username;

    @Size(max = 100, message = "Email must be less than 100 characters")
    @Email(message = "Please provide a valid email address")
    private String email;

    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])(?=\\S+$).*$",
            message = "Password must contain at least one digit, one lowercase, one uppercase, one special character, and no whitespace")
    private String newPassword;

    private String currentPassword;

    private String phoneNumber;

    private String profileImage;

    private Double loyaltyPoints;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String bankAccountName;
    private String bankAccountNumber;

    @Valid
    private List<UserVehiclePlateDTO> vehiclePlates;
}