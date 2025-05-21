package com.example.licenta.Repositories;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Models.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    Page<Reservation> findByUserId(String userId, Pageable pageable);

    Page<Reservation> findByUserIdAndReservationTypeIn(String userId, List<ReservationType> reservationTypes, Pageable pageable);

    // For getActiveReservation
    Optional<Reservation> findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeAfterAndStatusOrderByStartTimeDesc(
            String userId,
            ReservationType reservationType,
            OffsetDateTime currentTime,
            OffsetDateTime currentTimeAgain,
            ReservationStatus status
    );

    Optional<Reservation> findFirstByUserIdAndReservationTypeAndStartTimeBeforeAndEndTimeIsNullAndStatusOrderByStartTimeDesc(
            String userId,
            ReservationType reservationType,
            OffsetDateTime currentTime,
            ReservationStatus status
    );

    // For getUpcomingReservation
    Optional<Reservation> findFirstByUserIdAndReservationTypeAndStartTimeAfterAndStatusOrderByStartTimeAsc(
            String userId,
            ReservationType reservationType,
            OffsetDateTime currentTime,
            ReservationStatus status
    );
}