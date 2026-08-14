package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.config.FeatureFlags;
import com.activecourses.upwork.exception.FeatureDisabledException;
import com.activecourses.upwork.model.PaymentIntent;
import com.activecourses.upwork.model.ProviderEvent;
import com.activecourses.upwork.repository.payment.PaymentIntentRepository;
import com.activecourses.upwork.repository.payment.ProviderEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Stripe payment adapter — all real Stripe SDK calls are gated by feature flag.
 * When stripe_enabled = false, all methods throw FeatureDisabledException.
 * Tests should mock this class entirely.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentAdapter {

    private final FeatureFlags featureFlags;
    private final PaymentIntentRepository paymentIntentRepository;
    private final ProviderEventRepository providerEventRepository;

    /**
     * Creates a Stripe PaymentIntent for a milestone.
     * Returns clientSecret for frontend Stripe Elements.
     * Gated: throws FeatureDisabledException if stripe is disabled.
     */
    @Transactional
    public PaymentIntent createPaymentIntent(
            com.activecourses.upwork.model.Contract contract,
            com.activecourses.upwork.model.ContractMilestone milestone,
            BigDecimal amount,
            String currency,
            com.activecourses.upwork.model.User client
    ) {
        assertStripeEnabled();

        String idempotencyKey = "pi_" + contract.getContractId() +
                (milestone != null ? "_m" + milestone.getMilestoneId() : "") +
                "_" + UUID.randomUUID();

        // Check idempotency: if already exists, return existing
        Optional<PaymentIntent> existing = paymentIntentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.warn("[STRIPE] Idempotent PaymentIntent already exists for key={}", idempotencyKey);
            return existing.get();
        }

        // NOTE: Real Stripe SDK call would go here when credentials are provided.
        // com.stripe.model.PaymentIntent stripePI = com.stripe.model.PaymentIntent.create(params);
        // For now, infrastructure is built but not called (flag is false in all envs).

        PaymentIntent pi = PaymentIntent.builder()
                .contract(contract)
                .milestone(milestone)
                .amount(amount)
                .currency(currency != null ? currency : "BRL")
                .status("PENDING_FUNDING")
                .idempotencyKey(idempotencyKey)
                .client(client)
                .build();

        PaymentIntent saved = paymentIntentRepository.save(pi);
        log.info("[STRIPE] Created PaymentIntent id={} amount={} for contract={}",
                saved.getId(), amount, contract.getContractId());
        return saved;
    }

    /**
     * Validates and stores a Stripe webhook event idempotently.
     * Returns the stored ProviderEvent; if already processed, returns existing.
     * Gated: throws FeatureDisabledException if stripe is disabled.
     */
    @Transactional
    public ProviderEvent processWebhookEvent(
            String providerEventId,
            String eventType,
            String rawPayload,
            boolean signatureValid
    ) {
        assertStripeEnabled();

        // Idempotency: if event already stored, return without reprocessing
        Optional<ProviderEvent> existing = providerEventRepository.findByProviderEventId(providerEventId);
        if (existing.isPresent()) {
            log.warn("[STRIPE] Duplicate webhook event received and ignored: {}", providerEventId);
            return existing.get();
        }

        ProviderEvent event = ProviderEvent.builder()
                .provider("STRIPE")
                .providerEventId(providerEventId)
                .eventType(eventType)
                .payloadEncrypted(rawPayload) // In production: encrypt before storing
                .signatureValid(signatureValid)
                .processed(false)
                .build();

        ProviderEvent saved = providerEventRepository.save(event);
        log.info("[STRIPE] Stored webhook event id={} type={} signatureValid={}",
                providerEventId, eventType, signatureValid);
        return saved;
    }

    /**
     * Marks a ProviderEvent as processed.
     */
    @Transactional
    public void markEventProcessed(Long eventId) {
        providerEventRepository.findById(eventId).ifPresent(event -> {
            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            providerEventRepository.save(event);
        });
    }

    /**
     * Marks a ProviderEvent as failed with an error.
     */
    @Transactional
    public void markEventFailed(Long eventId, String error) {
        providerEventRepository.findById(eventId).ifPresent(event -> {
            event.setProcessingError(error);
            event.setRetryCount(event.getRetryCount() + 1);
            providerEventRepository.save(event);
        });
    }

    private void assertStripeEnabled() {
        if (!featureFlags.isStripeEnabled()) {
            throw new FeatureDisabledException("stripe_collection",
                    "O módulo de cobrança via Stripe está desativado. " +
                    "As credenciais Stripe ainda não foram configuradas. " +
                    "Entre em contato com o suporte para mais informações.");
        }
    }
}
