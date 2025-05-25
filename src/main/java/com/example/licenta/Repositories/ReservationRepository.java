package com.example.licenta.Repositories;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Models.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
}