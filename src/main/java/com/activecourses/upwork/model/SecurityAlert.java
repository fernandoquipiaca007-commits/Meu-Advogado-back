package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "security_alerts", indexes = {
    @Index(name = "idx_sa_type",     columnList = "alert_type"),
    @Index(name = "idx_sa_severity", columnList = "severity"),
    @Index(name = "idx_sa_resolved", columnList = "resolved")
})
public class SecurityAlert {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_type", nullable = false, length = 100)
    private String alertType;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String severity = "MEDIUM";

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "ip_address", length = 50) private String ipAddress;
    @Column(length = 500)                      private String endpoint;
    @Column(columnDefinition = "TEXT")         private String details;

    @Builder.Default @Column(nullable = false) private Boolean resolved = false;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at") private LocalDateTime resolvedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
