package com.example.licenta.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StripeIntentResponse {
    private String clientSecret;
    private String intentId;
    private String status; //"requires_payment_method", "requires_confirmation", "requires_action", "processing", "succeeded"
}