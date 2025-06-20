package com.example.licenta.Repositories;

import com.example.licenta.Models.Withdrawal;
import com.example.licenta.Models.Withdrawal.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, String> {

    Page<Withdrawal> findByUserIdOrderByRequestedAtDesc(String userId, Pageable pageable);

    boolean existsByUserIdAndStatusIn(String userId, List<Withdrawal.WithdrawalStatus> statuses);

    @Modifying
    @Query("SELECT w FROM Withdrawal w WHERE w.id = :id")
    void refresh(@Param("id") String id);

    Page<Withdrawal> findByUserIdAndStatusOrderByRequestedAtDesc(String userId, WithdrawalStatus status, Pageable pageable);

    List<Withdrawal> findByUserIdAndRequestedAtAfterOrderByRequestedAtDesc(String userId, OffsetDateTime after);

    @Query("SELECT COUNT(w) FROM Withdrawal w WHERE w.user.id = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT SUM(w.amount) FROM Withdrawal w WHERE w.user.id = :userId AND w.status = :status")
    Double sumAmountByUserIdAndStatus(@Param("userId") String userId, @Param("status") WithdrawalStatus status);

}