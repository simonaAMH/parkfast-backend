package com.example.licenta.Repositories;

import com.example.licenta.Models.GuestAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface GuestAccessTokenRepository extends JpaRepository<GuestAccessToken, Long> {
    Optional<GuestAccessToken> findByTokenAndReservationId(String token, Long reservationId);
    Optional<GuestAccessToken> findByToken(String token); // Alternative lookup
    void deleteByExpiresAtBefore(OffsetDateTime now); // For cleanup
}