package com.example.licenta.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationDTO {

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @NotBlank(message = "Body cannot be blank")
    @Size(max = 2000, message = "Body cannot exceed 2000 characters")
    private String body;

    @Size(max = 50, message = "Type cannot exceed 50 characters")
    private String type;

    @Size(max = 255, message = "Reference ID cannot exceed 255 characters")
    private String referenceId;

    private Long userId;
}