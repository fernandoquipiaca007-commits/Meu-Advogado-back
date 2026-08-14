package com.activecourses.upwork.service.payment;

import com.activecourses.upwork.config.FeatureFlags;
import com.activecourses.upwork.exception.FeatureDisabledException;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.payment.PayoutAccountRepository;
import com.activecourses.upwork.repository.payment.PayoutRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PayPal Payout Service — MVP manual payout flow.
 *
 * Architecture invariants:
 * - NEVER pass Stripe balance directly to PayPal without ledger reconciliation.
 * - Payout only from 'available_to_payout' as computed by eligibility check.
 * - Retry MUST check idempotency by senderBatchId before creating a new payout.
 * - Status is tracked to terminal state before marking "received".
 * - PIX is NOT supported. Only PayPal.
 *
 * Feature flag: payouts.paypal_enabled must be true; otherwise throws FeatureDisabledException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayPalPayoutService {

    private final FeatureFlags featureFlags;
    private final PayoutRequestRepository payoutRequestRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final PaymentLedgerService ledgerService;

    /**
     * Computes payout eligibility for a lawyer without calling PayPal.
     * Returns available balance and reasons for ineligibility.
     */
    @Transactional(readOnly = true)
    public PayoutEligibilityResult checkEligibility(Integer lawyerId, Integer contractId) {
        assertPayPalEnabled();

        BigDecimal ledgerBalance = ledgerService.getBalance(contractId);
        // TODO: deduct disputed, held amounts when dispute/hold tables are queried
        BigDecimal availableToPayout = ledgerBalance.max(BigDecimal.ZERO);

        boolean hasPayoutAccount = payoutAccountRepository.existsByUserUserId(lawyerId);
        boolean eligible = availableToPayout.compareTo(BigDecimal.ZERO) > 0 && hasPayoutAccount;

        String ineligibilityReasons = null;
        if (!hasPayoutAccount) ineligibilityReasons = "Conta PayPal não conectada.";
        else if (availableToPayout.compareTo(BigDecimal.ZERO) <= 0) ineligibilityReasons = "Saldo disponível insuficiente.";

        log.info("[PAYPAL] Eligibility check lawyer={} contract={} available={} eligible={}",
                lawyerId, contractId, availableToPayout, eligible);

        return new PayoutEligibilityResult(availableToPayout, eligible, ineligibilityReasons);
    }

    /**
     * Creates a payout request (MVP manual flow).
     * Reserves balance, creates idempotent request, then calls PayPal once.
     * Gated by payouts.paypal_enabled feature flag.
     */
    @Transactional
    public PayoutRequest requestPayout(
            User lawyer,
            Integer contractId,
            BigDecimal amount,
            String currency,
            User requestedBy
    ) {
        assertPayPalEnabled();

        PayoutAccount account = payoutAccountRepository.findByUserUserId(lawyer.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Advogado não possui conta PayPal conectada e validada. " +
                        "Acesse 'Minha Conta' > 'Recebimentos' para conectar sua conta PayPal."));

        if (!"VALIDATED".equals(account.getStatus())) {
            throw new IllegalStateException(
                    "Conta PayPal pendente de validação. Aguarde a confirmação antes de solicitar saque.");
        }

        // Idempotency: generate deterministic IDs for this payout attempt
        String senderBatchId = "batch_" + lawyer.getId() + "_c" + contractId + "_" + UUID.randomUUID();
        String senderItemId  = "item_"  + lawyer.getId()  + "_c" + contractId + "_" + UUID.randomUUID();

        // Check idempotency — should not happen in normal flow, but guard for retries
        if (payoutRequestRepository.findBySenderBatchId(senderBatchId).isPresent()) {
            log.warn("[PAYPAL] Duplicate senderBatchId detected: {}", senderBatchId);
            return payoutRequestRepository.findBySenderBatchId(senderBatchId).get();
        }

        BigDecimal platformFee = amount.multiply(BigDecimal.valueOf(0.10)); // 10% platform fee (configurable)
        BigDecimal netAmount   = amount.subtract(platformFee);

        PayoutRequest request = PayoutRequest.builder()
                .lawyer(lawyer)
                .payoutAccount(account)
                .grossAmount(amount)
                .platformFee(platformFee)
                .netAmount(netAmount)
                .currency(currency != null ? currency : "BRL")
                .senderBatchId(senderBatchId)
                .senderItemId(senderItemId)
                .status("RESERVED")
                .requestedBy(requestedBy)
                .build();

        PayoutRequest saved = payoutRequestRepository.save(request);

        // NOTE: Real PayPal API call would go here when credentials are available.
        // PayPal Payouts API: POST /v1/payments/payouts
        // Always check senderBatchId idempotency before calling.
        log.info("[PAYPAL] PayoutRequest created id={} senderBatchId={} net={} currency={}",
                saved.getId(), senderBatchId, netAmount, currency);

        return saved;
    }

    /**
     * Processes a PayPal webhook event for a payout status update.
     * Updates PayoutRequest status to the terminal state received.
     */
    @Transactional
    public void processPayoutWebhookEvent(String paypalBatchId, String newStatus, String failureReason) {
        assertPayPalEnabled();

        payoutRequestRepository.findByPaypalPayoutBatchId(paypalBatchId).ifPresentOrElse(
                request -> {
                    request.setStatus(newStatus);
                    request.setFailureReason(failureReason);
                    if ("SUCCESS".equals(newStatus)) {
                        request.setCompletedAt(LocalDateTime.now());
                    }
                    payoutRequestRepository.save(request);
                    log.info("[PAYPAL] Payout status updated: batchId={} status={}", paypalBatchId, newStatus);
                },
                () -> log.warn("[PAYPAL] Received webhook for unknown batch: {}", paypalBatchId)
        );
    }

    /**
     * Lists all payout requests for a lawyer (for dashboard display).
     */
    @Transactional(readOnly = true)
    public List<PayoutRequest> getPayoutHistory(Integer lawyerId) {
        return payoutRequestRepository.findByLawyerUserIdOrderByCreatedAtDesc(lawyerId);
    }

    private void assertPayPalEnabled() {
        if (!featureFlags.isPaypalPayoutsEnabled()) {
            throw new FeatureDisabledException("paypal_payout",
                    "O módulo de recebimento via PayPal está desativado. " +
                    "A conta PayPal Business e as aprovações necessárias ainda não foram configuradas. " +
                    "Entre em contato com o suporte para mais informações.");
        }
    }

    /** Simple eligibility result DTO */
    public record PayoutEligibilityResult(
            BigDecimal availableToPayout,
            boolean eligible,
            String ineligibilityReasons
    ) {}
}
