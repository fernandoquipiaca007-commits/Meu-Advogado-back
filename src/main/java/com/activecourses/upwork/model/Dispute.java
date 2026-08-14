package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "disputes", indexes = {
    @Index(name = "idx_disp_contract", columnList = "contract_id"),
    @Index(name = "idx_disp_status",   columnList = "status")
})
public class Dispute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @Column(name = "reason_category", nullable = false, length = 100) private String reasonCategory;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "evidence_summary", columnDefinition = "TEXT") private String evidenceSummary;

    @Builder.Default
    @Column(name = "frozen_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal frozenAmount = BigDecimal.ZERO;

    @Builder.Default @Column(nullable = false, length = 50) private String status = "OPEN";
    @Column(length = 100) private String decision;
    @Column(name = "decision_reason", columnDefinition = "TEXT") private String decisionReason;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at") private LocalDateTime decidedAt;
    @Column(name = "external_channel_url") private String externalChannelUrl;

    @Builder.Default @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); if (updatedAt == null) updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
