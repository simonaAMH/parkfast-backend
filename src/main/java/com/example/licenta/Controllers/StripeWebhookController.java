package com.example.licenta.Controllers;

import com.example.licenta.Exceptions.InvalidDataException;
import com.example.licenta.Services.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class StripeWebhookController {

    @Autowired
    private StripeWebhookService stripeWebhookService;

    @Value("${stripe.webhook.endpoint.secret}")
    private String endpointSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (InvalidDataException e) {
            log.error("Invalid JSON payload in webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON");
        } catch (SignatureVerificationException e) {
            log.error("Invalid signature in webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        switch (event.getType()) {
            case "charge.succeeded":
                stripeWebhookService.handleChargeSucceeded(event);
                break;
            case "payment_intent.succeeded":
                stripeWebhookService.handlePaymentIntentSucceeded(event);
                break;
            default:
                log.info("Unhandled event type: {}", event.getType());
        }

        return ResponseEntity.ok("Success");
    }
}