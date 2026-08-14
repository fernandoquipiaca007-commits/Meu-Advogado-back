package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "conflict_checks", uniqueConstraints = {
        @UniqueConstraint(name = "uq_conflict_checks_job_lawyer", columnNames = {"job_id", "lawyer_id"})
}, indexes = {
        @Index(name = "idx_conflict_checks_job", columnList = "job_id"),
        @Index(name = "idx_conflict_checks_lawyer", columnList = "lawyer_id"),
        @Index(name = "idx_conflict_checks_status", columnList = "status")
})
public class ConflictCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private User lawyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private ConflictStatus status = ConflictStatus.NOT_STARTED;

    @Column(name = "reason_masked", length = 255)
    private String reasonMasked;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ConflictStatus.NOT_STARTED;
        }
    }
}
