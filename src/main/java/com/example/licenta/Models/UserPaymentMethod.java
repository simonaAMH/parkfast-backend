package com.example.licenta.Models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_payment_methods")
@Getter
@Setter
@NoArgsConstructor
public class UserPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "card_number", length = 24, nullable = false)
    private String cardNumber;

    @Column(name = "expiry_month", nullable = false)
    @Min(1) @Max(12)
    private short expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

}