package com.example.licenta.DTOs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewDTO {
    private String id;
    private Integer rating;
    private String comment;
    private String reservationId;
    private String userId;
    private String reviewerDisplayName;
    private OffsetDateTime createdAt;
}