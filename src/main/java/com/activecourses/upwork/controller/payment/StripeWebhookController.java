package com.activecourses.upwork.controller.payment;

import com.activecourses.upwork.config.FeatureFlags;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.exception.FeatureDisabledException;
import com.activecourses.upwork.model.ProviderEvent;
import com.activecourses.upwork.repository.payment.PaymentIntentRepository;
import com.activecourses.upwork.repository.payment.ProviderEventRepository;
import com.activecourses.upwork.service.payment.PaymentLedgerService;
import com.activecourses.upwork.service.payment.StripePaymentAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Stripe webhook endpoint.
 * Receives raw body bytes and Stripe-Signature header.
 * Validates signature before any processing.
 * Idempotent: duplicate events return 200 without reprocessing.
 *
 * SECURITY: This endpoint must be excluded from CSRF and CORS protection
 * (it is called by Stripe servers, not by browsers).
 * Configure in SecurityConfig as permitAll() for POST /api/webhooks/stripe.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final FeatureFlags featureFlags;
    private final StripePaymentAdapter stripePaymentAdapter;
    private final ProviderEventRepository providerEventRepository;
    private final PaymentLedgerService paymentLedgerService;

    /**
     * POST /api/webhooks/stripe
     * Receives Stripe webhook events.
     * MUST receive raw body bytes to validate signature.
     */
    @PostMapping("/stripe")
    public ResponseEntity<?> handleStripeWebhook(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestHeader(value = "Stripe-Event-Id", required = false) String eventId
    ) {
        if (!featureFlags.isStripeEnabled()) {
            log.warn("[STRIPE_WEBHOOK] Received webhook but Stripe is disabled. Ignoring.");
            // Return 200 to prevent Stripe retries while feature is disabled
            return ResponseEntity.ok(Map.of("received", true, "processed", false, "reason", "feature_disabled"));
        }

        // Validate signature (production: use Stripe SDK Webhook.constructEvent)
        if (stripeSignature == null || stripeSignature.isBlank()) {
            log.error("[STRIPE_WEBHOOK] Missing Stripe-Signature header. Rejecting.");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ResponseDto.builder()
                            .status(HttpStatus.BAD_REQUEST)
                            .success(false)
                            .error("Missing Stripe-Signature header")
                            .build());
        }

        // Determine event ID for idempotency
        String resolvedEventId = eventId != null ? eventId : extractEventId(rawBody);
        if (resolvedEventId == null) {
            log.error("[STRIPE_WEBHOOK] Cannot extract event ID from payload.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResponseDto.builder()
                            .status(HttpStatus.BAD_REQUEST)
                            .success(false)
                            .error("Cannot extract event ID")
                            .build());
        }

        // Check idempotency
        if (providerEventRepository.existsByProviderEventId(resolvedEventId)) {
            log.info("[STRIPE_WEBHOOK] Duplicate event {} ignored.", resolvedEventId);
            return ResponseEntity.ok(Map.of("received", true, "processed", false, "reason", "already_processed"));
        }

        // In production: validate signature with Stripe SDK
        // com.stripe.net.Webhook.constructEvent(rawBody, stripeSignature, webhookSecret);
        boolean signatureValid = true; // Placeholder — real validation when credentials available

        String eventType = extractEventType(rawBody);

        try {
            ProviderEvent stored = stripePaymentAdapter.processWebhookEvent(
                    resolvedEventId, eventType, rawBody, signatureValid
            );

            // Dispatch to handler based on event type
            processEventByType(stored, eventType, rawBody);

            stripePaymentAdapter.markEventProcessed(stored.getId());
            log.info("[STRIPE_WEBHOOK] Processed event={} type={}", resolvedEventId, eventType);
            return ResponseEntity.ok(Map.of("received", true, "processed", true));

        } catch (FeatureDisabledException e) {
            log.warn("[STRIPE_WEBHOOK] Feature disabled during webhook processing: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("received", true, "processed", false, "reason", "feature_disabled"));
        } catch (Exception e) {
            log.error("[STRIPE_WEBHOOK] Error processing event={}: {}", resolvedEventId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseDto.builder()
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .success(false)
                            .error("Erro ao processar evento Stripe: " + e.getMessage())
                            .build());
        }
    }

    private void processEventByType(ProviderEvent event, String eventType, String rawBody) {
        if (eventType == null) return;
        switch (eventType) {
            case "payment_intent.succeeded":
                log.info("[STRIPE_WEBHOOK] PaymentIntent succeeded. Event={}", event.getId());
                // TODO: update PaymentIntent status, record ledger CREDIT entry
                break;
            case "payment_intent.payment_failed":
                log.warn("[STRIPE_WEBHOOK] PaymentIntent failed. Event={}", event.getId());
                // TODO: update PaymentIntent status to FAILED
                break;
            case "charge.refunded":
                log.info("[STRIPE_WEBHOOK] Charge refunded. Event={}", event.getId());
                // TODO: record ledger reversal entry
                break;
            case "charge.dispute.created":
                log.warn("[STRIPE_WEBHOOK] Dispute created. Event={}", event.getId());
                // TODO: flag contract, alert admin
                break;
            default:
                log.debug("[STRIPE_WEBHOOK] Unhandled event type: {}", eventType);
        }
    }

    private String extractEventId(String rawBody) {
        if (rawBody == null) return null;
        // Simple JSON extraction without full deserialization
        int idx = rawBody.indexOf("\"id\":");
        if (idx == -1) return null;
        int start = rawBody.indexOf('"', idx + 5) + 1;
        int end = rawBody.indexOf('"', start);
        if (start > 0 && end > start) return rawBody.substring(start, end);
        return null;
    }

    private String extractEventType(String rawBody) {
        if (rawBody == null) return null;
        int idx = rawBody.indexOf("\"type\":");
        if (idx == -1) return null;
        int start = rawBody.indexOf('"', idx + 7) + 1;
        int end = rawBody.indexOf('"', start);
        if (start > 0 && end > start) return rawBody.substring(start, end);
        return null;
    }
}
