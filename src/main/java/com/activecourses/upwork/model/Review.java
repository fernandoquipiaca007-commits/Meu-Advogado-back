package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_reviews_contract",   columnList = "contract_id"),
    @Index(name = "idx_reviews_reviewer",   columnList = "reviewer_id"),
    @Index(name = "idx_reviews_reviewee",   columnList = "reviewee_id"),
    @Index(name = "idx_reviews_moderation", columnList = "moderation_status")
})
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private User reviewee;

    /** Score 1-5 (no automatic zero) */
    @Column(nullable = false)
    private Integer rating;

    /** Structured dimension scores (optional) */
    @Column(name = "communication_score") private Integer communicationScore;
    @Column(name = "quality_score")       private Integer qualityScore;
    @Column(name = "timeliness_score")    private Integer timelinessScore;

    @Column(columnDefinition = "TEXT")
    private String comment;

    /** Hidden until both parties submit or deadline passes (blind review) */
    @Builder.Default
    @Column(name = "is_revealed", nullable = false)
    private Boolean isRevealed = false;

    /** Moderation lifecycle: PENDING → APPROVED | REJECTED | UNDER_REVIEW */
    @Builder.Default
    @Column(name = "moderation_status", nullable = false, length = 50)
    private String moderationStatus = "PENDING";

    @Column(name = "moderation_note", columnDefinition = "TEXT") private String moderationNote;

    @Builder.Default @Column(name = "is_reported", nullable = false) private Boolean isReported = false;
    @Column(name = "report_reason", columnDefinition = "TEXT") private String reportReason;

    @Builder.Default @Column(name = "is_visible", nullable = false) private Boolean isVisible = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
