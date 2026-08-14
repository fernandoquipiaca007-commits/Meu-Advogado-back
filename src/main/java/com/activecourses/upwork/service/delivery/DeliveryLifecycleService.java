package com.activecourses.upwork.service.delivery;

import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.delivery.DeliveryRepository;
import com.activecourses.upwork.repository.delivery.DisputeRepository;
import com.activecourses.upwork.repository.delivery.CancellationRequestRepository;
import com.activecourses.upwork.repository.delivery.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Delivery Lifecycle Service — manages the full contract closure cycle:
 * delivery submission, change requests, acceptance, cancellation, dispute, and reviews.
 *
 * Invariants:
 * - No milestone is released without a formal delivery and client acceptance.
 * - No silent cancellation acceptance (MVP).
 * - Disputes address operational/financial matters only — not legal/ethical merit.
 * - Reviews are blind until both parties submit (or deadline passes).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryLifecycleService {

    private final DeliveryRepository deliveryRepository;
    private final DisputeRepository disputeRepository;
    private final CancellationRequestRepository cancellationRepository;
    private final ReviewRepository reviewRepository;

    // ─────────────────────────────────────────────────────────────
    // DELIVERY FLOW
    // ─────────────────────────────────────────────────────────────

    /**
     * Lawyer submits formal delivery for a milestone.
     * Status: SUBMITTED
     */
    @Transactional
    public Delivery submitDelivery(Contract contract, ContractMilestone milestone,
                                   User lawyer, String description,
                                   String criteriaSatisfied, String limitationsNoted) {
        // Check if previous delivery exists — increment version
        int version = deliveryRepository.findByContractContractIdAndMilestoneMilestoneId(
                contract.getContractId(), milestone != null ? milestone.getMilestoneId() : null)
                .stream().mapToInt(Delivery::getVersion).max().orElse(0) + 1;

        // Supersede any previous SUBMITTED delivery
        deliveryRepository.findByContractContractIdAndStatus(contract.getContractId(), "SUBMITTED")
                .forEach(d -> { d.setStatus("SUPERSEDED"); deliveryRepository.save(d); });

        Delivery delivery = Delivery.builder()
                .contract(contract).milestone(milestone).submittedBy(lawyer)
                .version(version).status("SUBMITTED")
                .description(description)
                .criteriaSatisfied(criteriaSatisfied).limitationsNoted(limitationsNoted)
                .build();

        Delivery saved = deliveryRepository.save(delivery);
        log.info("[DELIVERY] Submitted v{} for contract={} by lawyer={}", version, contract.getContractId(), lawyer.getId());
        return saved;
    }

    /**
     * Client requests a change on the delivery (not acceptance yet).
     */
    @Transactional
    public Delivery requestChange(Long deliveryId, User client, String changeReason) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Entrega não encontrada: " + deliveryId));

        if (!"SUBMITTED".equals(delivery.getStatus())) {
            throw new IllegalStateException("Pedido de alteração só é possível para entregas com status SUBMITTED.");
        }

        delivery.setStatus("CHANGE_REQUESTED");
        delivery.setChangeRequestReason(changeReason);
        delivery.setClientViewedAt(LocalDateTime.now());

        Delivery saved = deliveryRepository.save(delivery);
        log.info("[DELIVERY] Change requested on delivery={} by client={}", deliveryId, client.getId());
        return saved;
    }

    /**
     * Client formally accepts delivery → triggers RELEASE_PENDING on milestone.
     * Financial release is handled by Phase 4/5 providers, NOT here.
     */
    @Transactional
    public Delivery acceptDelivery(Long deliveryId, User client) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Entrega não encontrada: " + deliveryId));

        if (!"SUBMITTED".equals(delivery.getStatus())) {
            throw new IllegalStateException("Aceite só é possível para entregas com status SUBMITTED.");
        }

        delivery.setStatus("ACCEPTED");
        delivery.setAcceptedBy(client);
        delivery.setAcceptedAt(LocalDateTime.now());
        delivery.setClientViewedAt(LocalDateTime.now());

        Delivery saved = deliveryRepository.save(delivery);
        log.info("[DELIVERY] Accepted delivery={} by client={} → RELEASE_PENDING", deliveryId, client.getId());
        // Domain event: milestone transitions to RELEASE_PENDING (handled by milestoneService in future phase)
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // CANCELLATION FLOW
    // ─────────────────────────────────────────────────────────────

    /**
     * Initiates a cancellation request. Counterpart must actively respond.
     * No silent acceptance in MVP.
     */
    @Transactional
    public CancellationRequest initiateCancellation(Contract contract, User initiator,
                                                     String reasonCategory, String reasonDetail,
                                                     BigDecimal proposedClientPct, BigDecimal proposedLawyerPct) {
        CancellationRequest request = CancellationRequest.builder()
                .contract(contract).initiatedBy(initiator)
                .reasonCategory(reasonCategory).reasonDetail(reasonDetail)
                .proposedClientPct(proposedClientPct).proposedLawyerPct(proposedLawyerPct)
                .status("PENDING")
                .build();

        CancellationRequest saved = cancellationRepository.save(request);
        log.info("[CANCEL] Cancellation initiated for contract={} by user={} reason={}",
                contract.getContractId(), initiator.getId(), reasonCategory);
        return saved;
    }

    /**
     * Counterpart responds to cancellation: ACCEPTED, REJECTED, or COUNTER_PROPOSED.
     */
    @Transactional
    public CancellationRequest respondToCancellation(Long requestId, User responder,
                                                      String response, String note) {
        CancellationRequest req = cancellationRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido de cancelamento não encontrado: " + requestId));

        if (!"PENDING".equals(req.getStatus()) && !"NEGOTIATING".equals(req.getStatus())) {
            throw new IllegalStateException("Pedido de cancelamento não está em estado de resposta.");
        }

        req.setCounterpartResponse(response);
        req.setCounterpartNote(note);
        req.setCounterpartRespondedAt(LocalDateTime.now());
        req.setStatus("ACCEPTED".equals(response) ? "ACCEPTED" : "NEGOTIATING");
        if ("ACCEPTED".equals(response)) req.setResolvedAt(LocalDateTime.now());

        CancellationRequest saved = cancellationRepository.save(req);
        log.info("[CANCEL] Response={} for requestId={} by user={}", response, requestId, responder.getId());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // DISPUTE FLOW
    // ─────────────────────────────────────────────────────────────

    /**
     * Opens an operational dispute. Freezes specified amount.
     * Platform decides financial outcome. Legal/ethical matters → external channel.
     */
    @Transactional
    public Dispute openDispute(Contract contract, User opener, String reasonCategory,
                               String description, BigDecimal frozenAmount) {
        Dispute dispute = Dispute.builder()
                .contract(contract).openedBy(opener)
                .reasonCategory(reasonCategory).description(description)
                .frozenAmount(frozenAmount != null ? frozenAmount : BigDecimal.ZERO)
                .status("OPEN")
                .build();

        Dispute saved = disputeRepository.save(dispute);
        log.info("[DISPUTE] Opened for contract={} by user={} frozen={}",
                contract.getContractId(), opener.getId(), frozenAmount);
        return saved;
    }

    /**
     * Platform resolves dispute with a financial decision.
     * Does NOT assign legal/professional guilt.
     */
    @Transactional
    public Dispute resolveDispute(Long disputeId, User decider, String decision, String decisionReason) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Disputa não encontrada: " + disputeId));

        dispute.setDecision(decision);
        dispute.setDecisionReason(decisionReason);
        dispute.setDecidedBy(decider);
        dispute.setDecidedAt(LocalDateTime.now());
        dispute.setStatus("DECIDED");

        Dispute saved = disputeRepository.save(dispute);
        log.info("[DISPUTE] Resolved id={} decision={}", disputeId, decision);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // REVIEW FLOW
    // ─────────────────────────────────────────────────────────────

    /**
     * Submits a structured review. Hidden until both parties submit (blind review).
     * Score must be 1-5; no automatic zero allowed.
     */
    @Transactional
    public Review submitReview(Contract contract, User reviewer, User reviewee,
                                     int score, String comment,
                                     Integer commScore, Integer qualScore, Integer timeScore) {
        if (score < 1 || score > 5) throw new IllegalArgumentException("Nota deve ser entre 1 e 5.");

        if (reviewRepository.existsByContractContractIdAndReviewerIdAndRevieweeId(
                contract.getContractId(), reviewer.getId(), reviewee.getId())) {
            throw new IllegalStateException("Avaliação já submetida para este contrato.");
        }

        Review review = Review.builder()
                .contract(contract).reviewer(reviewer).reviewee(reviewee)
                .rating(score).comment(comment)
                .communicationScore(commScore).qualityScore(qualScore).timelinessScore(timeScore)
                .isRevealed(false).moderationStatus("PENDING")
                .build();

        Review saved = reviewRepository.save(review);
        log.info("[REVIEW] Submitted by={} for reviewee={} score={}", reviewer.getId(), reviewee.getId(), score);

        // Check if both parties have submitted → reveal both reviews
        revealIfBothSubmitted(contract);
        return saved;
    }

    /**
     * Reveals both reviews if both parties have submitted.
     */
    private void revealIfBothSubmitted(Contract contract) {
        List<Review> reviews = reviewRepository.findByContractContractId(contract.getContractId());
        if (reviews.size() >= 2) {
            reviews.forEach(r -> { r.setIsRevealed(true); reviewRepository.save(r); });
            log.info("[REVIEW] Both reviews submitted for contract={} — revealing.", contract.getContractId());
        }
    }

    /**
     * Reports a review for moderation.
     */
    @Transactional
    public Review reportReview(Long reviewId, User reporter, String reason) {
        Review review = reviewRepository.findById(reviewId.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Avaliação não encontrada: " + reviewId));
        review.setIsReported(true);
        review.setReportReason(reason);
        review.setModerationStatus("UNDER_REVIEW");
        Review saved = reviewRepository.save(review);
        log.info("[REVIEW] Reported id={} reason={}", reviewId, reason);
        return saved;
    }
}
