package com.example.licenta.Repositories;

import com.example.licenta.Models.GuestAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface GuestAccessTokenRepository extends JpaRepository<GuestAccessToken, String> {
    Optional<GuestAccessToken> findByTokenAndReservationId(String token, String reservationId);
    Optional<GuestAccessToken> findByReservationId(String reservationId);
    Optional<GuestAccessToken> findByToken(String token);
    void deleteByExpiresAtBefore(OffsetDateTime now);
}