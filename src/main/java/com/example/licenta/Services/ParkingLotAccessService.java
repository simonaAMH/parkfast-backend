package com.example.licenta.Services;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Exceptions.ResourceNotFoundException;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.Reservation;
import com.example.licenta.Models.User;
import com.example.licenta.Repositories.ParkingLotRepository;
import com.example.licenta.Repositories.ReservationRepository;
import com.example.licenta.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ParkingLotAccessService {

    private final UserRepository userRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final ReservationRepository reservationRepository;

    @Autowired
    public ParkingLotAccessService(UserRepository userRepository,
                                   ParkingLotRepository parkingLotRepository,
                                   ReservationRepository reservationRepository
    ) {
        this.userRepository = userRepository;
        this.parkingLotRepository = parkingLotRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public void gpsCheckInUser(String userId, String parkingLotId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));

        if (user.getCurrentParkingLotId() != null) {
            if (!user.getCurrentParkingLotId().equals(parkingLotId)) {
                throw new InvalidDataException("User is already checked into a different parking lot.");
            } else {
                throw new InvalidDataException("User is already marked as inside this lot.");
            }
        }

        List<ReservationStatus> eligibleStatuses = List.of(ReservationStatus.PAID, ReservationStatus.ACTIVE);

        Reservation reservationToProcess = reservationRepository
                .findTopByUserIdAndParkingLotIdAndHasCheckedInFalseAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
                        userId, parkingLotId, eligibleStatuses)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No eligible reservation found at this parking lot. Reservation must be PAID or ACTIVE and not yet checked in."));

        if (!Objects.equals(reservationToProcess.getParkingLot().getId(), parkingLotId)
                            || !Objects.equals(reservationToProcess.getUser().getId(), userId)){
            throw new InvalidDataException("User does not have a valid reservation for this parking lot.");
        }

        if (user.getCurrentParkingLotId() == null || !user.getCurrentParkingLotId().equals(parkingLotId)) {
            user.setCurrentParkingLotId(parkingLotId);
            userRepository.save(user);
        }

        reservationToProcess.setHasCheckedIn(true);
        reservationToProcess.setHasCheckedOut(false);
        reservationRepository.save(reservationToProcess);
    }


    @Transactional
    public void gpsCheckInGuest(String deviceIdentifier, String parkingLotId) {
         ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
         .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));

        List<ReservationStatus> eligibleStatuses = List.of(ReservationStatus.PAID, ReservationStatus.ACTIVE);

        Reservation reservationToProcess = reservationRepository
                .findTopByUserIsNullAndDeviceIdentifierAndParkingLotIdAndHasCheckedInFalseAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
                        deviceIdentifier, parkingLotId, eligibleStatuses)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Guest (Device: " + deviceIdentifier + "): No eligible reservation found at parking lot " + parkingLotId +
                                ". Reservation must be PAID or ACTIVE and not yet checked in."));

        if (reservationToProcess.getUser() != null || !Objects.equals(reservationToProcess.getParkingLot().getId(), parkingLotId)) {
            throw new InvalidDataException("Mismatch: Found reservation " + reservationToProcess.getId() + " is not a valid guest reservation for the specified lot/device for GPS check-in.");
        }

        reservationToProcess.setHasCheckedIn(true);
        reservationToProcess.setHasCheckedOut(false);
        reservationRepository.save(reservationToProcess);
        System.out.println("Guest (Device: " + deviceIdentifier + ") GPS checked into lot " + parkingLotId + " for reservation " + reservationToProcess.getId());
    }

    @Transactional
    public void gpsCheckOutUser(String userId, String parkingLotId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));

        if (user.getCurrentParkingLotId() == null) {
            throw new InvalidDataException("User is not currently marked as in any lot.");
        }
        if (!user.getCurrentParkingLotId().equals(parkingLotId)) {
            throw new InvalidDataException("User is marked as inside a different lot than the one he attempted exit for.");
        }

        List<ReservationStatus> eligibleStatuses = List.of(ReservationStatus.PAID);

        Reservation reservationToProcess = reservationRepository
                .findTopByUserIdAndParkingLotIdAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
                        userId, parkingLotId, eligibleStatuses)
                .orElseThrow(() -> new ResourceNotFoundException( "No eligible reservation found at this parking lot. Reservation must be PAID and not yet checked out."));

        if (!Objects.equals(reservationToProcess.getParkingLot().getId(), parkingLotId)
                || !Objects.equals(reservationToProcess.getUser().getId(), userId)){
            throw new InvalidDataException("User does not have a valid reservation for this parking lot.");
        }

        if (user.getCurrentParkingLotId().equals(parkingLotId)) {
            user.setCurrentParkingLotId(null);
            userRepository.save(user);
        }

        reservationToProcess.setHasCheckedIn(true);
        reservationToProcess.setHasCheckedOut(true);
        reservationRepository.save(reservationToProcess);
    }

    @Transactional
    public void gpsCheckOutGuest(String deviceIdentifier, String parkingLotId) {
         ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                 .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));

        List<ReservationStatus> eligibleStatuses = List.of(ReservationStatus.PAID, ReservationStatus.ACTIVE);

        Reservation reservationToProcess = reservationRepository
                .findTopByUserIsNullAndDeviceIdentifierAndParkingLotIdAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
                        deviceIdentifier, parkingLotId, eligibleStatuses)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Guest (Device: " + deviceIdentifier + "): No eligible active reservation found at parking lot " + parkingLotId +
                                " to check out from."));

        if (reservationToProcess.getUser() != null || !Objects.equals(reservationToProcess.getParkingLot().getId(), parkingLotId)) {
            throw new InvalidDataException("Mismatch: Found reservation " + reservationToProcess.getId() + " is not a valid guest reservation for the specified lot/device for GPS check-out.");
        }

        reservationToProcess.setHasCheckedOut(true);
        reservationToProcess.setHasCheckedIn(true);
        reservationRepository.save(reservationToProcess);
        System.out.println("Guest (Device: " + deviceIdentifier + ") GPS checked out from lot " + parkingLotId + " for reservation " + reservationToProcess.getId());
    }

    @Transactional
    public void barrierVerifyEntry(String plateNumber, String parkingLotId) {
        String normalizedPlate = plateNumber.toUpperCase().replaceAll("\\s+", "");
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));
        List<ReservationStatus> eligibleEntryStatuses = List.of(ReservationStatus.PAID, ReservationStatus.ACTIVE);

        // 1. Try registered user's reservation
        Optional<Reservation> userReservationOpt = reservationRepository
                .findTopByVehiclePlateAndParkingLotIdAndUserIsNotNullAndHasCheckedInFalseAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
                        normalizedPlate, parkingLotId, eligibleEntryStatuses);

        if (userReservationOpt.isPresent()) {
            Reservation reservation = userReservationOpt.get();
            User user = reservation.getUser();

            if (user.getCurrentParkingLotId() != null) {
                if (!user.getCurrentParkingLotId().equals(parkingLotId) || reservation.isHasCheckedIn()) {
                    throw new InvalidDataException("User " + user.getUsername() + " (plate " + normalizedPlate + ") is already in lot " + user.getCurrentParkingLotId() + ". Cannot enter lot " + parkingLotId);
                }
            }

            if (user.getCurrentParkingLotId() == null) {
                user.setCurrentParkingLotId(parkingLotId);
                userRepository.save(user);
            }

            reservation.setHasCheckedIn(true);
            reservation.setHasCheckedOut(false);
            reservationRepository.save(reservation);
            return;
        }

        // 2. Try guest's pre-booked reservation
        Optional<Reservation> guestReservationOpt = reservationRepository
                .findTopByVehiclePlateAndParkingLotIdAndUserIsNullAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
                        normalizedPlate, parkingLotId, eligibleEntryStatuses);

        if (guestReservationOpt.isPresent()) {
            Reservation reservation = guestReservationOpt.get();
            reservation.setHasCheckedIn(true);
            reservation.setHasCheckedOut(false);
            reservationRepository.save(reservation);
            return;
        }

        throw new ResourceNotFoundException(
                "No eligible reservation found for plate " + normalizedPlate + " at lot " + parkingLotId);
    }

    @Transactional
    public void barrierVerifyExit(String plateNumber, String parkingLotId) {
        String normalizedPlate = plateNumber.toUpperCase().replaceAll("\\s+", "");
        ParkingLot parkingLot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking Lot not found: " + parkingLotId));
        List<ReservationStatus> activeExitStatus = List.of(ReservationStatus.PAID);

        // 1. Try registered user's active session
        Optional<Reservation> userReservationOpt = reservationRepository
                .findTopByVehiclePlateAndParkingLotIdAndUserIsNotNullAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
                        normalizedPlate, parkingLotId, activeExitStatus);

        if (userReservationOpt.isPresent()) {
            Reservation reservation = userReservationOpt.get();
            User user = reservation.getUser();

            if (user.getCurrentParkingLotId() == null) {
                throw new InvalidDataException("User " + user.getId() + " is not currently marked as in any lot (based on User.currentParkingLotId).");
            }
            if (!user.getCurrentParkingLotId().equals(parkingLotId)) {
                throw new InvalidDataException("User " + user.getId() + " is marked as in lot " + user.getCurrentParkingLotId() +
                        ", but " + " exit attempted for lot " + parkingLotId);
            }

            user.setCurrentParkingLotId(null);
            userRepository.save(user);
            reservation.setHasCheckedOut(true);
            reservation.setHasCheckedIn(true);
            reservationRepository.save(reservation);
            return;
        }

        // 2. Try guest's active session
        Optional<Reservation> guestReservationOpt = reservationRepository
                .findTopByVehiclePlateAndParkingLotIdAndUserIsNullAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
                        normalizedPlate, parkingLotId, activeExitStatus);

        if (guestReservationOpt.isPresent()) {
            Reservation reservation = guestReservationOpt.get();
            reservation.setHasCheckedOut(true);
            reservation.setHasCheckedIn(true);
            reservationRepository.save(reservation);
            return;
        }

        // 3. No active session found for this plate to exit
        throw new ResourceNotFoundException(
                "No active reservation found for plate " + normalizedPlate + " at lot " + parkingLotId + " to process barrier exit.");
    }

    @Transactional
    public void handleQrScan(String qrCodeData) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (qrCodeData == null || qrCodeData.trim().isEmpty()) {
            throw new InvalidDataException("QR code data cannot be empty.");
        }

        String[] parts = qrCodeData.split(":");
        if (parts.length != 2) {
            throw new InvalidDataException("Invalid QR code data format. Expected 'reservationId:token'. Received: " + qrCodeData);
        }

        String reservationIdFromQr = parts[0];
        String tokenFromQr = parts[1];

        if (reservationIdFromQr.trim().isEmpty() || tokenFromQr.trim().isEmpty()) {
            throw new InvalidDataException("Reservation ID or token in QR data is empty.");
        }

        Reservation reservation = reservationRepository.findByIdAndActiveQrToken(reservationIdFromQr, tokenFromQr)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invalid or unknown QR Code."));

        if (reservation.getQrTokenExpiry() != null && reservation.getQrTokenExpiry().isBefore(now)) {
            reservation.setActiveQrToken(null);
            reservation.setQrTokenExpiry(null);
            reservationRepository.save(reservation);
            throw new InvalidDataException("QR Code has expired for reservation " + reservation.getId() +
                    ". Expiry: " + reservation.getQrTokenExpiry() + ", Current Time: " + now);
        }

        User user = reservation.getUser();
        ParkingLot parkingLot = reservation.getParkingLot();
        if (parkingLot == null) {
            throw new ResourceNotFoundException("Parking lot associated with the QR code not found.");
        }

        if (!reservation.isHasCheckedIn()) { //qr scan for entry
            if (!(reservation.getStatus() == ReservationStatus.PAID || reservation.getStatus() == ReservationStatus.ACTIVE)) {
                throw new InvalidDataException("Reservation is not in PAID or ACTIVE status for check-in.");
            }

            if (user != null) {
                String currentLotId = user.getCurrentParkingLotId();

                if (currentLotId != null) {
                    if (Objects.equals(currentLotId, parkingLot.getId())) {
                        throw new InvalidDataException("User is marked as already checked in this lot. Cannot process QR scan.");
                    } else {
                        throw new InvalidDataException("User is currently inside a different lot. Cannot process QR scan.");
                    }
                }
            }

            reservation.setHasCheckedIn(true);
            reservation.setHasCheckedOut(false);
            reservationRepository.save(reservation);

            if (user != null) {
                user.setCurrentParkingLotId(parkingLot.getId());
                userRepository.save(user);
            }
        } else if (!reservation.isHasCheckedOut()) { //qr scan for exit
            if (reservation.getStatus() != ReservationStatus.PAID) {
                throw new InvalidDataException("Reservation is not in PAID status for check-out.");
            }

            if (user != null) { // if its a user reservation, not guest
                String currentLotId = user.getCurrentParkingLotId();

                if (currentLotId == null) {
                    throw new InvalidDataException("User is not checked-in in this lot. Cannot process QR scan.");
                } else if (!currentLotId.equals(parkingLot.getId())) {
                    throw new InvalidDataException("User is marked as inside a different lot than the one he attempted exit for. Cannot process QR scan.");
                }
            }

            reservation.setHasCheckedOut(true);
            reservation.setHasCheckedIn(true);
            reservationRepository.save(reservation);

            if (user != null) {
                user.setCurrentParkingLotId(null);
                userRepository.save(user);
            }
        } else {
            throw new InvalidDataException("The user of this reservation has already checked out. Cannot process QR scan.");
        }

            reservation.setActiveQrToken(null);
            reservation.setQrTokenExpiry(null);
            reservationRepository.save(reservation);
        }
}