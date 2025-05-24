package com.example.licenta.Repositories;

import com.example.licenta.Models.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END FROM Review r WHERE r.reservation.id = :reservationId")
    boolean existsByReservationId(@Param("reservationId") String reservationId);
    Optional<Review> findByReservationId(String reservationId);
    @Query("SELECT r FROM Review r WHERE r.reservation.parkingLot.id = :parkingLotId")
    Page<Review> findReviewsByParkingLotId(@Param("parkingLotId") String parkingLotId, Pageable pageable);
    @Query("SELECT r FROM Review r WHERE r.reservation.parkingLot.id = :parkingLotId")
    List<Review> findAllByReservationParkingLotId(@Param("parkingLotId") String parkingLotId);
}