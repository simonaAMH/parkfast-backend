package com.example.licenta.DTOs;

import com.example.licenta.Enum.User.Role;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegistrationDTO {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "Username can only contain letters, numbers and underscores")
    private String username;

    @NotBlank(message = "Email is required")
    @Size(max = 100, message = "Email must be less than 100 characters")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 40, message = "Password must be between 8 and 40 characters")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?])(?=\\S+$).*$",
            message = "Password must contain at least one digit, one lowercase, one uppercase, one special character, and no whitespace")
    private String password;

    @Size(max = 255, message = "Profile image URL is too long")
    @Pattern(regexp = "^(https?://.*|)$", message = "Profile image must be a valid URL or empty")
    private String profileImage;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;
}