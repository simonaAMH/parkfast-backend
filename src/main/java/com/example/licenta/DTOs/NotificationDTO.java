package com.example.licenta.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private Long userId;
    private String title;
    private String body;
    private String type;
    private String referenceId;
    private boolean isRead;
    private LocalDateTime createdAt;
}