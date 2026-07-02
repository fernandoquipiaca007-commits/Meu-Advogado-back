package com.activecourses.upwork.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "jobs", indexes = {
    @Index(name = "idx_jobs_urgency", columnList = "urgency"),
    @Index(name = "idx_jobs_status", columnList = "status"),
    @Index(name = "idx_jobs_specialty", columnList = "specialty_id"),
    @Index(name = "idx_jobs_archived", columnList = "archived")
})
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer jobId;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private User client;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Legal case fields
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private UrgencyLevel urgency = UrgencyLevel.Medium;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private ConfidentialityLevel confidentiality = ConfidentialityLevel.Public;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedValue;

    private LocalDate deadline;

    @ManyToOne
    @JoinColumn(name = "specialty_id")
    private Specialty specialty;

    @Builder.Default
    private boolean archived = false;

    private LocalDateTime archivedAt;

    private LocalDateTime closedAt;

    @ManyToMany
    @JoinTable(
            name = "job_skills",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> skills;
}