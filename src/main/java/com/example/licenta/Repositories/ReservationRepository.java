package com.example.licenta.Repositories;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Models.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {
    Page<Reservation> findByUserId(String userId, Pageable pageable);
    Page<Reservation> findByUserIdAndReservationTypeIn(String userId, List<ReservationType> types, Pageable pageable);

    // For findActiveReservation and hasActiveOrUpcomingReservationForLot (STANDARD, DIRECT)
    Optional<Reservation> findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatusOrderByStartTimeDesc(
            String userId, ReservationType type, OffsetDateTime now1, OffsetDateTime now2, ReservationStatus status);

    // For findActiveReservation and hasActiveOrUpcomingReservationForLot (PAY_FOR_USAGE)
    Optional<Reservation> findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeIsNullAndStatusOrderByStartTimeDesc(
            String userId, ReservationType type, OffsetDateTime now, ReservationStatus status);

    Optional<Reservation> findFirstByUserIdAndReservationTypeAndStartTimeAfterAndStatusOrderByStartTimeAsc(
            String userId, ReservationType type, OffsetDateTime startTimeAfter, ReservationStatus status
    );

    Optional<Reservation> findFirstByUserIdAndReservationTypeInAndStartTimeAfterAndStatusInOrderByStartTimeAsc(
            String userId, Collection<ReservationType> types, OffsetDateTime startTimeAfter, Collection<ReservationStatus> statuses
    );

    List<Reservation> findAllByUserIdAndParkingLotIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatus(
            String userId, String parkingLotId, ReservationType reservationType,
            OffsetDateTime startTimeCriteria, OffsetDateTime endTimeCriteria, ReservationStatus status);

    List<Reservation> findAllByUserIdAndParkingLotIdAndReservationTypeAndStartTimeBeforeAndEndTimeIsNullAndStatus(
            String userId, String parkingLotId, ReservationType reservationType,
            OffsetDateTime startTimeCriteria, ReservationStatus status);

    List<Reservation> findAllByUserIdAndParkingLotIdAndReservationTypeAndStartTimeAfterAndStartTimeBeforeAndStatus(
            String userId, String parkingLotId, ReservationType reservationType,
            OffsetDateTime startTimeAfterCriteria, OffsetDateTime startTimeBeforeCriteria, ReservationStatus status);

    List<Reservation> findAllByUserIdAndParkingLotIdAndReservationTypeInAndStartTimeAfterAndStartTimeBeforeAndStatusIn(
            String userId, String parkingLotId, List<ReservationType> reservationTypes,
            OffsetDateTime startTimeAfterCriteria, OffsetDateTime startTimeBeforeCriteria, List<ReservationStatus> statuses);

    Optional<Reservation> findByIdAndActiveQrToken(String reservationId, String activeQrToken);

    Optional<Reservation> findByStripePaymentIntentId(String stripePaymentIntentId);

    // Guest GPS Check-in
    Optional<Reservation> findTopByUserIsNullAndDeviceIdentifierAndParkingLotIdAndHasCheckedInFalseAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
            String deviceIdentifier, String parkingLotId, List<ReservationStatus> statuses);

    // Guest GPS Check-out
    Optional<Reservation> findTopByUserIsNullAndDeviceIdentifierAndParkingLotIdAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
            String deviceIdentifier, String parkingLotId, List<ReservationStatus> statuses);

    // User GPS Check-in
    Optional<Reservation> findTopByUserIdAndParkingLotIdAndHasCheckedInFalseAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
            String userId, String parkingLotId, List<ReservationStatus> validStatuses);

    // User GPS Check-out
    Optional<Reservation> findTopByUserIdAndParkingLotIdAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
            String userId, String parkingLotId, List<ReservationStatus> validStatuses);

    // barrier entry - User
    Optional<Reservation> findTopByVehiclePlateAndParkingLotIdAndUserIsNotNullAndHasCheckedInFalseAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
            String vehiclePlate, String parkingLotId, List<ReservationStatus> validStatuses);

    // barrier entry - Guest
    Optional<Reservation> findTopByVehiclePlateAndParkingLotIdAndUserIsNullAndHasCheckedInFalseAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
            String vehiclePlate, String parkingLotId, List<ReservationStatus> validStatuses);

    // barrier exit - User
    Optional<Reservation> findTopByVehiclePlateAndParkingLotIdAndUserIsNotNullAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
            String vehiclePlate, String parkingLotId, List<ReservationStatus> validStatuses);

    // barrier exit - Guest
    Optional<Reservation> findTopByVehiclePlateAndParkingLotIdAndUserIsNullAndHasCheckedInTrueAndHasCheckedOutFalseAndStatusInOrderByStartTimeAsc(
            String vehiclePlate, String parkingLotId, List<ReservationStatus> validStatuses);

    // ALL
    Page<Reservation> findByParkingLotId(String parkingLotId, Pageable pageable);

    // "ACTIVE" reservations for a specific parking lot
    @Query("SELECT r FROM Reservation r WHERE r.parkingLot.id = :parkingLotId AND (" +
            "(r.reservationType = com.example.licenta.Enum.Reservation.ReservationType.PAY_FOR_USAGE AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.ACTIVE AND r.endTime IS NULL) OR " +
            "(r.reservationType IN (com.example.licenta.Enum.Reservation.ReservationType.STANDARD, com.example.licenta.Enum.Reservation.ReservationType.DIRECT) AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.PAID AND r.startTime <= :now AND r.endTime >= :now)" +
            ")")
    Page<Reservation> findActiveReservationsForParkingLot(@Param("parkingLotId") String parkingLotId, @Param("now") OffsetDateTime now, Pageable pageable);

    // "UPCOMING" reservations for a specific parking lot
    @Query("SELECT r FROM Reservation r WHERE r.parkingLot.id = :parkingLotId " +
            "AND r.startTime > :now " +
            "AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.PAID")
    Page<Reservation> findUpcomingReservationsForParkingLot(@Param("parkingLotId") String parkingLotId, @Param("now") OffsetDateTime now, Pageable pageable);

    // "ENDED" reservations for a specific parking lot
    @Query("SELECT r FROM Reservation r WHERE r.parkingLot.id = :parkingLotId " +
            "AND r.endTime < :now " +
            "AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.PAID")
    Page<Reservation> findEndedReservationsForParkingLot(@Param("parkingLotId") String parkingLotId, @Param("now") OffsetDateTime now, Pageable pageable);
}