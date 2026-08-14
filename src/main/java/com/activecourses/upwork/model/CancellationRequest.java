package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "cancellation_requests", indexes = {
    @Index(name = "idx_cancel_contract", columnList = "contract_id"),
    @Index(name = "idx_cancel_status",   columnList = "status")
})
public class CancellationRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "initiated_by", nullable = false)
    private User initiatedBy;

    @Column(name = "reason_category", nullable = false, length = 100) private String reasonCategory;
    @Column(name = "reason_detail", columnDefinition = "TEXT") private String reasonDetail;

    @Column(name = "proposed_client_pct", precision = 5, scale = 2) private java.math.BigDecimal proposedClientPct;
    @Column(name = "proposed_lawyer_pct", precision = 5, scale = 2) private java.math.BigDecimal proposedLawyerPct;

    @Builder.Default @Column(name = "deliveries_handoff_done", nullable = false) private Boolean deliveriesHandoffDone = false;
    @Builder.Default @Column(name = "documents_handoff_done", nullable = false)  private Boolean documentsHandoffDone  = false;
    @Builder.Default @Column(name = "access_revoked", nullable = false)           private Boolean accessRevoked          = false;
    @Builder.Default @Column(name = "deadline_alert_sent", nullable = false)      private Boolean deadlineAlertSent      = false;

    @Column(name = "counterpart_response", length = 50) private String counterpartResponse;
    @Column(name = "counterpart_note", columnDefinition = "TEXT") private String counterpartNote;
    @Column(name = "counterpart_responded_at") private LocalDateTime counterpartRespondedAt;

    @Builder.Default @Column(nullable = false, length = 50) private String status = "PENDING";
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;

    @Builder.Default @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); if (updatedAt == null) updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
