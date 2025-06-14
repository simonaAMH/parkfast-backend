package com.example.licenta.Models;

import com.example.licenta.Enum.User.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

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
    private Double loyaltyPoints = 0.0;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "account_deletion_token")
    private String accountDeletionToken;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "token_expiry")
    private OffsetDateTime tokenExpiry;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "pending_earnings")
    private Double pendingEarnings = 0.0;

    @Column(name = "total_earnings")
    private Double totalEarnings = 0.0;

    @Column(name = "paid_earnings")
    private Double paidEarnings = 0.0;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParkingLot> parkingLots = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    @Column(name = "current_parking_lot_id")
    private String currentParkingLotId;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "stripe_connected_account_id", nullable = true, length = 255)
    private String stripeConnectedAccountId;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}