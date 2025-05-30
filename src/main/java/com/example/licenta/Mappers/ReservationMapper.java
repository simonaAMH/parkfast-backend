package com.example.licenta.Mappers;

import com.example.licenta.DTOs.ReservationDTO;
import com.example.licenta.Models.Reservation;
import org.springframework.stereotype.Service;

@Service
public class ReservationMapper {

    public ReservationDTO toDTO(Reservation reservation) {
        if (reservation == null) {
            return null;
        }

        ReservationDTO dto = new ReservationDTO();
        dto.setId(reservation.getId());
        if (reservation.getParkingLot() != null) {
            dto.setParkingLotId(reservation.getParkingLot().getId());
        }
        if (reservation.getUser() != null) {
            dto.setUserId(reservation.getUser().getId());
        }
        if (reservation.getReview() != null) {
            dto.setReviewId(reservation.getReview().getId());
        }
        dto.setDeviceIdentifier(reservation.getDeviceIdentifier());
        dto.setStartTime(reservation.getStartTime());
        dto.setEndTime(reservation.getEndTime());
        dto.setVehiclePlate(reservation.getVehiclePlate());
        dto.setPhoneNumber(reservation.getPhoneNumber());
        dto.setGuestEmail(reservation.getGuestEmail());
        dto.setGuestName(reservation.getGuestName());
        dto.setTotalAmount(reservation.getTotalAmount());
        dto.setPointsUsed(reservation.getPointsUsed());
        dto.setFinalAmount(reservation.getFinalAmount());
        dto.setReservationType(reservation.getReservationType());
        dto.setStatus(reservation.getStatus());
        dto.setActiveQrToken(reservation.getActiveQrToken());
        dto.setQrTokenExpiry(reservation.getQrTokenExpiry());
        dto.setHasCheckedIn(reservation.isHasCheckedIn());
        dto.setHasCheckedOut(reservation.isHasCheckedOut());
        dto.setCreatedAt(reservation.getCreatedAt());
        dto.setUpdatedAt(reservation.getUpdatedAt());

        return dto;
    }
}