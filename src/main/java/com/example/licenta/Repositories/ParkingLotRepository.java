package com.example.licenta.Repositories;

import com.example.licenta.Enum.ParkingLot.ParkingLotStatus;
import com.example.licenta.Enum.ParkingLot.PaymentTiming;
import com.example.licenta.Models.ParkingLot;
import com.example.licenta.Models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParkingLotRepository extends JpaRepository<ParkingLot, String> {

    Page<ParkingLot> findByOwner(User owner, Pageable pageable);
    Page<ParkingLot> findByAllowDirectPaymentTrue(Pageable pageable);
    List<ParkingLot> findByStatus(ParkingLotStatus status);
    List<ParkingLot> findByOwner(User owner);
    Page<ParkingLot> findByOwnerId(String ownerId, Pageable pageable);

    Page<ParkingLot> findByAllowReservationsTrue(Pageable pageable);

    Page<ParkingLot> findByAllowReservationsTrueAndPaymentTiming(PaymentTiming paymentTiming, Pageable pageable);

    Page<ParkingLot> findByAllowReservationsTrueAndPaymentTimingEquals(PaymentTiming paymentTiming, Pageable pageable);

    @Query(value = "SELECT * FROM parking_lots p " +
            "WHERE p.gps_coordinates IS NOT NULL " +
            "AND p.status = 'ACTIVE' " +
            "AND (6371 * acos(" +
            "    cos(radians(:latitude)) * " +
            "    cos(radians(CAST(trim(split_part(p.gps_coordinates, ',', 1)) AS DOUBLE PRECISION))) * " +
            "    cos(radians(CAST(trim(split_part(p.gps_coordinates, ',', 2)) AS DOUBLE PRECISION)) - radians(:longitude)) + " +
            "    sin(radians(:latitude)) * " +
            "    sin(radians(CAST(trim(split_part(p.gps_coordinates, ',', 1)) AS DOUBLE PRECISION)))" +
            ")) <= :radiusKm", nativeQuery = true)
    Page<ParkingLot> findNearbyParkingLots(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusKm") Double radiusKm,
            Pageable pageable);
}