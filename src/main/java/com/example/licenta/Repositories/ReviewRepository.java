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
public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByReservationId(Long reservationId);
    Optional<Review> findByReservationId(Long reservationId);
    @Query("SELECT r FROM Review r WHERE r.reservation.parkingLot.id = :parkingLotId")
    Page<Review> findReviewsByParkingLotId(@Param("parkingLotId") Long parkingLotId, Pageable pageable);
    @Query("SELECT r FROM Review r WHERE r.reservation.parkingLot.id = :parkingLotId")
    List<Review> findAllByReservationParkingLotId(@Param("parkingLotId") Long parkingLotId);
}