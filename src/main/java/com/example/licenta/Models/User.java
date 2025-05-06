package com.example.licenta.Models;

import com.example.licenta.Enum.User.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Email
    @Column(unique = true)
    private String email;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(unique = true)
    private String username;

    @NotBlank
    @Size(min = 8, max = 255)
    private String password;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(length = 10)
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    @Column(name = "loyalty_points")
    private Integer loyaltyPoints = 0;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "account_deletion_token")
    private String accountDeletionToken;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "token_expiry")
    private LocalDateTime tokenExpiry;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParkingLot> parkingLots = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addParkingLot(ParkingLot parkingLot) {
        if (parkingLot != null) {
            parkingLots.add(parkingLot);
            parkingLot.setOwner(this);
        }
    }

    public void removeParkingLot(ParkingLot parkingLot) {
        if (parkingLot != null && parkingLots.contains(parkingLot)) {
            parkingLots.remove(parkingLot);
            parkingLot.setOwner(null);
        }
    }
}