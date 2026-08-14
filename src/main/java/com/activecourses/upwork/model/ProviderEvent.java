package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "provider_events", indexes = {
    @Index(name = "idx_pe_provider_event_id", columnList = "provider_event_id"),
    @Index(name = "idx_pe_processed",         columnList = "processed"),
    @Index(name = "idx_pe_event_type",        columnList = "event_type")
})
public class ProviderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String provider = "STRIPE";

    @Column(name = "provider_event_id", nullable = false, unique = true, length = 255)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload_encrypted", columnDefinition = "TEXT")
    private String payloadEncrypted;

    @Builder.Default
    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "processing_error", columnDefinition = "TEXT")
    private String processingError;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Builder.Default
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) receivedAt = LocalDateTime.now();
    }
}
