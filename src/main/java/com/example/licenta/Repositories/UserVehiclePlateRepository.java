package com.example.licenta.Repositories;

import com.example.licenta.Models.UserVehiclePlate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserVehiclePlateRepository extends JpaRepository<UserVehiclePlate, Long> {
    List<UserVehiclePlate> findByUserId(Long userId);

    Optional<UserVehiclePlate> findByUserIdAndId(Long userId, Long id);

    boolean existsByUserIdAndPlateNumber(Long userId, String plateNumber);
}