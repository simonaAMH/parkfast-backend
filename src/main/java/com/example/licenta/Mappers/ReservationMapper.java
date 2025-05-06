package com.example.licenta.Mappers;

import com.example.licenta.DTOs.ReservationDTO;
import com.example.licenta.Models.Reservation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
            dto.setParkingLotName(reservation.getParkingLot().getName());
        }
        if (reservation.getUser() != null) {
            dto.setUserId(reservation.getUser().getId());
            dto.setUsername(reservation.getUser().getUsername());
        }
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
        dto.setQrCodeData(reservation.getQrCodeData());
        dto.setCreatedAt(reservation.getCreatedAt());
        dto.setUpdatedAt(reservation.getUpdatedAt());

        return dto;
    }

    public List<ReservationDTO> toDTOList(List<Reservation> reservations) {
        return reservations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}