package com.example.licenta.Services;

import com.example.licenta.DTOs.CreateReservationDTO;
import com.example.licenta.DTOs.ReservationDTO;
import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Mappers.ReservationMapper;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.Reservation;
import com.example.licenta.Models.User;
import com.example.licenta.Repositories.ParkingLotRepository;
import com.example.licenta.Repositories.ReservationRepository;
import com.example.licenta.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Random;
import java.math.RoundingMode;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final UserRepository userRepository;
    private final ReservationMapper reservationMapper;
    private static final Random random = new Random();

    @Autowired
    public ReservationService(ReservationRepository reservationRepository,
                              ParkingLotRepository parkingLotRepository,
                              UserRepository userRepository,
                              ReservationMapper reservationMapper ) {
        this.reservationRepository = reservationRepository;
        this.parkingLotRepository = parkingLotRepository;
        this.userRepository = userRepository;
        this.reservationMapper = reservationMapper;
    }

    @Transactional
    public ReservationDTO createDirectReservation(CreateReservationDTO dto) {
        ParkingLot parkingLot = parkingLotRepository.findById(dto.getParkingLotId())
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + dto.getParkingLotId()));

        User user = null;
        if (dto.getUserId() != null) {
            user = userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + dto.getUserId()));
        }

        if (!parkingLot.isAllowDirectPayment()) {
            throw new InvalidDataException("Direct payment is not allowed for this parking lot.");
        }

        OffsetDateTime startTime;
        OffsetDateTime endTime = null;
        try {
            startTime = OffsetDateTime.parse(dto.getStartTime());

            if(dto.getEndTime() != null ){
                endTime = OffsetDateTime.parse(dto.getEndTime());
            }

        } catch (DateTimeParseException e) {
            System.err.println("DateTimeParseException during reservation creation: " + e.getMessage());
            throw new InvalidDataException("Invalid date format. Please use ISO 8601 format.");
        }

        if (dto.getEndTime() != null && (endTime.isBefore(startTime) || endTime.isEqual(startTime))) {
            throw new InvalidDataException("End time must be after start time.");
        }

        Reservation reservation = new Reservation();
        reservation.setParkingLot(parkingLot);
        reservation.setUser(user);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setVehiclePlate(dto.getVehiclePlate());
        reservation.setPhoneNumber(dto.getPhoneNumber());
        if (user == null) {
            reservation.setGuestEmail(dto.getGuestEmail());
            reservation.setGuestName(dto.getGuestName());
        }
        reservation.setTotalAmount(dto.getTotalAmount());
        reservation.setPointsUsed(dto.getPointsUsed());
        reservation.setFinalAmount(dto.getFinalAmount());
        reservation.setReservationType(dto.getReservationType());
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);

        Reservation savedReservation = reservationRepository.save(reservation);

        return reservationMapper.toDTO(savedReservation);
    }

    @Transactional(readOnly = true)
    public ReservationDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
        return reservationMapper.toDTO(reservation);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePrice(Long parkingLotId, OffsetDateTime startTime, OffsetDateTime endTime) {
        if (parkingLotId == null) {
            throw new InvalidDataException("Parking lot ID is required.");
        }
        if (startTime == null || endTime == null) {
            throw new InvalidDataException("Start time and end time are required.");
        }


        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));

        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            throw new InvalidDataException("End time must be after start time.");
        }

        double randomPrice = 1.0 + (100.0 * random.nextDouble());
        BigDecimal price = BigDecimal.valueOf(randomPrice).setScale(2, RoundingMode.HALF_UP);
        System.out.println("Generated Mock Price: " + price + " for Lot ID: " + parkingLotId + " Start: " + startTime + " End: " + endTime); // Log for debugging
        return price;
    }


    @Transactional(readOnly = true)
    public Page<ReservationDTO> getReservationsByUserId(Long userId, List<ReservationType> types, Pageable pageable) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Page<Reservation> reservationPage;
        if (types == null || types.isEmpty()) {
            reservationPage = reservationRepository.findByUserId(userId, pageable);
        } else {
            reservationPage = reservationRepository.findByUserIdAndReservationTypeIn(userId, types, pageable);
        }

        List<ReservationDTO> dtoList = reservationPage.getContent().stream()
                .map(reservationMapper::toDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, reservationPage.getTotalElements());
    }

    @Transactional
    public ReservationDTO updateReservationStatus(Long reservationId, ReservationStatus newStatus, Integer pointsUsed, BigDecimal finalAmount) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        reservation.setStatus(newStatus);

        if (pointsUsed != null) {
            reservation.setPointsUsed(pointsUsed);
        }
        if (finalAmount != null) {
            reservation.setFinalAmount(finalAmount);
        }

        Reservation updatedReservation = reservationRepository.save(reservation);

        // TODO: Add side effects based on status change (e.g., sending notifications)

        return reservationMapper.toDTO(updatedReservation);
    }

    @Transactional(readOnly = true)
    public Optional<ReservationDTO> findActiveReservation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        OffsetDateTime now = OffsetDateTime.now();

        Optional<Reservation> standardActive = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatusOrderByStartTimeDesc(
                userId,
                ReservationType.STANDARD,
                now,
                now,
                ReservationStatus.PAID
        );

        Optional<Reservation> payForUsageActive = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeIsNullAndStatusOrderByStartTimeDesc(
                userId,
                ReservationType.PAY_FOR_USAGE,
                now,
                ReservationStatus.ACTIVE
        );

        if (standardActive.isPresent() && payForUsageActive.isPresent()) {
            Reservation standard = standardActive.get();
            Reservation payForUsage = payForUsageActive.get();

            return Optional.of(reservationMapper.toDTO(
                    standard.getStartTime().isAfter(payForUsage.getStartTime()) ? standard : payForUsage
            ));
        }

        if (standardActive.isPresent()) {
            return Optional.of(reservationMapper.toDTO(standardActive.get()));
        }

        if (payForUsageActive.isPresent()) {
            return Optional.of(reservationMapper.toDTO(payForUsageActive.get()));
        }

        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ReservationDTO> findUpcomingReservation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        OffsetDateTime now = OffsetDateTime.now();

        Optional<Reservation> standardUpcoming = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeAfterAndStatusOrderByStartTimeAsc(
                userId,
                ReservationType.STANDARD,
                now,
                ReservationStatus.PAID
        );

        Optional<Reservation> payForUsageUpcoming = reservationRepository.findFirstByUserIdAndReservationTypeAndStartTimeAfterAndStatusOrderByStartTimeAsc(
                userId,
                ReservationType.PAY_FOR_USAGE,
                now,
                ReservationStatus.ACTIVE
        );

        if (standardUpcoming.isPresent() && payForUsageUpcoming.isPresent()) {
            Reservation standard = standardUpcoming.get();
            Reservation payForUsage = payForUsageUpcoming.get();

            return Optional.of(reservationMapper.toDTO(
                    standard.getStartTime().isBefore(payForUsage.getStartTime()) ? standard : payForUsage
            ));
        }

        if (standardUpcoming.isPresent()) {
            return Optional.of(reservationMapper.toDTO(standardUpcoming.get()));
        }

        if (payForUsageUpcoming.isPresent()) {
            return Optional.of(reservationMapper.toDTO(payForUsageUpcoming.get()));
        }

        return Optional.empty();
    }

    @Transactional
    public ReservationDTO endActiveReservation(Long reservationId, OffsetDateTime endTime, BigDecimal totalAmount) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidDataException("Only active reservations can be ended.");
        }

        reservation.setEndTime(endTime);
        reservation.setTotalAmount(totalAmount);
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);

        Reservation updatedReservation = reservationRepository.save(reservation);
        return reservationMapper.toDTO(updatedReservation);
    }
}