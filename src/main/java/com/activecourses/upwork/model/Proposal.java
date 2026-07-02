package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "proposals", indexes = {
    @Index(name = "idx_proposals_job", columnList = "job_id"),
    @Index(name = "idx_proposals_lawyer", columnList = "freelancer_id"),
    @Index(name = "idx_proposals_status", columnList = "status")
})
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer proposalId;

    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne
    @JoinColumn(name = "freelancer_id", nullable = false)
    private User lawyer;

    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    @Column(precision = 10, scale = 2)
    private BigDecimal proposedRate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProposalStatus status = ProposalStatus.Pending;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Legal-specific fields
    private Integer proposedDuration;

    @Column(columnDefinition = "TEXT")
    private String strategy;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalValue;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
