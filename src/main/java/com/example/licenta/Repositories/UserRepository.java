package com.example.licenta.Repositories;

import com.example.licenta.Models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmailVerificationToken(String token);
    Optional<User> findByPasswordResetToken(String token);
    Optional<User> findByAccountDeletionToken(String token);

    Boolean existsByEmail(String email);
    Boolean existsByUsername(String username);
}