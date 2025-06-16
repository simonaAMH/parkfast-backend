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
            log.info("Received Stripe webhook event: {} with ID: {}", event.getType(), event.getId());
        } catch (InvalidDataException e) {
            log.error("Invalid JSON payload in webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON");
        } catch (SignatureVerificationException e) {
            log.error("Invalid signature in webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Error constructing webhook event: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Webhook error");
        }

        try {
            switch (event.getType()) {
                case "charge.succeeded":
                    log.info("Processing charge.succeeded event");
                    stripeWebhookService.handleChargeSucceeded(event);
                    break;

                case "payment_intent.succeeded":
                    log.info("Processing payment_intent.succeeded event");
                    stripeWebhookService.handlePaymentIntentSucceeded(event);
                    break;

                case "payment_intent.payment_failed":
                    log.info("Processing payment_intent.payment_failed event");
                    stripeWebhookService.handlePaymentIntentPaymentFailed(event);
                    break;

                case "setup_intent.succeeded":
                    log.info("Processing setup_intent.succeeded event");
                    stripeWebhookService.handleSetupIntentSucceeded(event);
                    break;

                case "setup_intent.setup_failed":
                    log.info("Processing setup_intent.setup_failed event");
                    stripeWebhookService.handleSetupIntentSetupFailed(event);
                    break;

                case "payment_intent.requires_action":
                    log.info("Processing payment_intent.requires_action event");
                    break;

                case "payment_intent.processing":
                    log.info("Processing payment_intent.processing event");
                    break;

                case "payment_method.attached":
                    log.info("Processing payment_method.attached event");
                    break;

                default:
                    log.info("Unhandled event type: {} - Event ID: {}", event.getType(), event.getId());
            }

            log.info("Successfully processed webhook event: {} with ID: {}", event.getType(), event.getId());
            return ResponseEntity.ok("Success");

        } catch (Exception e) {
            log.error("Error processing webhook event {} with ID {}: {}",
                    event.getType(), event.getId(), e.getMessage(), e);

            // For critical payment events, you might want to return an error to trigger retry
            if (isCriticalEvent(event.getType())) {
                return ResponseEntity.status(500).body("Processing error - will retry");
            }

            // For non-critical events, return success to prevent retries
            return ResponseEntity.ok("Non-critical error logged");
        }
    }

    private boolean isCriticalEvent(String eventType) {
        return eventType.equals("payment_intent.succeeded") ||
                eventType.equals("payment_intent.payment_failed") ||
                eventType.equals("setup_intent.succeeded") ||
                eventType.equals("setup_intent.setup_failed") ||
                eventType.equals("charge.succeeded");
    }
}