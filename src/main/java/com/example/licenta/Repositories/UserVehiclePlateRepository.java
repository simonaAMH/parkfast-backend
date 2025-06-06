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
public interface UserVehiclePlateRepository extends JpaRepository<UserVehiclePlate, String> {
    List<UserVehiclePlate> findByUserId(String userId);
    Optional<UserVehiclePlate> findByUserIdAndId(String userId, String id);
    boolean existsByUserIdAndPlateNumber(String userId, String plateNumber);
}