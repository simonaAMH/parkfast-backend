package com.example.licenta.Repositories;

import com.example.licenta.Enum.Reservation.ReservationStatus;
import com.example.licenta.Enum.Reservation.ReservationType;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.Reservation;
import com.example.licenta.Models.Review;
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

    Optional<Reservation> findByStripeSetupIntentId(String stripeSetupIntentId);

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

    @Query("SELECT r FROM Reservation r WHERE r.parkingLot IN :parkingLots AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.PAID AND r.endTime >= :overallStartTime AND r.endTime <= :overallEndTime")
    List<Reservation> findPaidReservationsInDateRange(
            @Param("parkingLots") List<ParkingLot> parkingLots,
            @Param("overallStartTime") OffsetDateTime overallStartTime,
            @Param("overallEndTime") OffsetDateTime overallEndTime
    );

    @Query("SELECT r FROM Reservation r WHERE r.parkingLot IN :parkingLots AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.ACTIVE AND r.reservationType = com.example.licenta.Enum.Reservation.ReservationType.PAY_FOR_USAGE AND r.endTime IS NULL AND r.startTime <= :currentTime")
    List<Reservation> findActivePayForUsageReservationsForLots(
            @Param("parkingLots") List<ParkingLot> parkingLots,
            @Param("currentTime") OffsetDateTime currentTime
    );

    @Query("SELECT r FROM Reservation r WHERE r.parkingLot.id = :parkingLotId AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.PAID AND r.endTime >= :startTime AND r.endTime <= :endTime")
    List<Reservation> findPaidReservationsForLotInDateRange(
            @Param("parkingLotId") String parkingLotId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    @Query("SELECT r FROM Reservation r WHERE r.parkingLot.id = :parkingLotId AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.ACTIVE AND r.reservationType = com.example.licenta.Enum.Reservation.ReservationType.PAY_FOR_USAGE AND r.endTime IS NULL AND r.startTime <= :currentTime")
    List<Reservation> findActivePayForUsageReservationsForLot(
            @Param("parkingLotId") String parkingLotId,
            @Param("currentTime") OffsetDateTime currentTime
    );

    // For Customer Insights - Average Rating
    @Query("SELECT AVG(rev.rating) FROM Review rev WHERE rev.reservation.parkingLot.id = :parkingLotId")
    Double getAverageRatingForParkingLot(@Param("parkingLotId") String parkingLotId);

    // For Customer Insights - Repeat Customer Rate (Example: count users with > 1 reservation at this lot)
    // This is a simplified version. A more accurate one might need to look at distinct users over a longer period.
    @Query("SELECT COUNT(DISTINCT r.user.id) FROM Reservation r WHERE r.parkingLot.id = :parkingLotId AND r.user IS NOT NULL AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.PAID AND r.endTime <= :endTime")
    Long countDistinctUsersForLot(@Param("parkingLotId") String parkingLotId, @Param("endTime") OffsetDateTime endTime);

    @Query("SELECT COUNT(r.user.id) FROM Reservation r WHERE r.parkingLot.id = :parkingLotId AND r.user IS NOT NULL AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.PAID AND r.endTime <= :endTime GROUP BY r.user.id HAVING COUNT(r.user.id) > 1")
    List<Long> countUsersWithMultipleReservationsForLot(@Param("parkingLotId") String parkingLotId, @Param("endTime") OffsetDateTime endTime);


    // For Customer Insights - Popular Reservation Types
    @Query("SELECT r.reservationType, COUNT(r) FROM Reservation r WHERE r.parkingLot.id = :parkingLotId AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.PAID AND r.endTime <= :endTime AND r.endTime >= :startTime GROUP BY r.reservationType")
    List<Object[]> countReservationsByTypeForLot(
            @Param("parkingLotId") String parkingLotId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    // For Cancellation Rate
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.parkingLot.id = :parkingLotId AND r.status = com.example.licenta.Enum.Reservation.ReservationStatus.CANCELLED AND r.createdAt >= :startTime AND r.createdAt <= :endTime")
    Long countCancelledReservationsForLotInDateRange(
            @Param("parkingLotId") String parkingLotId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.parkingLot.id = :parkingLotId AND r.createdAt >= :startTime AND r.createdAt <= :endTime")
    Long countTotalAttemptedReservationsForLotInDateRange( // Includes paid and cancelled
                                                           @Param("parkingLotId") String parkingLotId,
                                                           @Param("startTime") OffsetDateTime startTime,
                                                           @Param("endTime") OffsetDateTime endTime);
    
}