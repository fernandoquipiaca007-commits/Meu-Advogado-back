package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "deliveries", indexes = {
    @Index(name = "idx_del_contract",  columnList = "contract_id"),
    @Index(name = "idx_del_milestone", columnList = "milestone_id"),
    @Index(name = "idx_del_status",    columnList = "status")
})
public class Delivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "milestone_id")
    private ContractMilestone milestone;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Builder.Default
    @Column(nullable = false) private Integer version = 1;

    @Builder.Default
    @Column(nullable = false, length = 50) private String status = "SUBMITTED";

    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "criteria_satisfied", columnDefinition = "TEXT") private String criteriaSatisfied;
    @Column(name = "limitations_noted", columnDefinition = "TEXT") private String limitationsNoted;
    @Column(name = "client_viewed_at") private LocalDateTime clientViewedAt;
    @Column(name = "change_request_reason", columnDefinition = "TEXT") private String changeRequestReason;
    @Column(name = "accepted_at") private LocalDateTime acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "accepted_by")
    private User acceptedBy;

    @Builder.Default @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); if (updatedAt == null) updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
