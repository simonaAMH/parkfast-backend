//package com.example.licenta.DTOs;
//
//import jakarta.validation.constraints.*;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//@Data
//@NoArgsConstructor
//public class UserPaymentMethodDTO {
//    private String id;
//
//    @NotBlank(message = "Card number is required")
//    @Size(min = 13, max = 24, message = "Invalid card number length")
//    @Pattern(regexp = "^[0-9]+$", message = "Card number must contain only digits")
//    private String cardNumber;
//
//    @NotNull(message = "Expiry month is required")
//    @Min(value = 1, message = "Expiry month must be between 1 and 12")
//    @Max(value = 12, message = "Expiry month must be between 1 and 12")
//    private Short expiryMonth;
//
//    @NotNull(message = "Expiry year is required")
//    @Min(value = 25, message = "Expiry year seems invalid")
//    private Integer expiryYear;
//}