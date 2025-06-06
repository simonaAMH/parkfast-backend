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
    boolean existsByReservationId(String reservationId);
    Optional<Review> findByReservationId(String reservationId);
    Page<Review> findByReservationParkingLotId(String parkingLotId, Pageable pageable);
    List<Review> findByReservationParkingLotId(String parkingLotId);
}
