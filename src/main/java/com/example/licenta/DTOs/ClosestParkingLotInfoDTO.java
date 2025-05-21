package com.example.licenta.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClosestParkingLotInfoDTO {
    private String parkingLotId;
    private Double distanceToUserInMeters;
}