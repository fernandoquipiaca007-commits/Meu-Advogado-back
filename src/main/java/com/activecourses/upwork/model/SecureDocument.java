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
@Table(name = "secure_documents", indexes = {
        @Index(name = "idx_secure_documents_contract", columnList = "contract_id"),
        @Index(name = "idx_secure_documents_job", columnList = "job_id"),
        @Index(name = "idx_secure_documents_owner", columnList = "owner_id"),
        @Index(name = "idx_secure_documents_hash", columnList = "sha256_hash"),
        @Index(name = "idx_secure_documents_classification", columnList = "classification")
})
public class SecureDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "sha256_hash", nullable = false, length = 128)
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private DocumentClassification classification = DocumentClassification.CONFIDENTIAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "virus_scan_status", nullable = false, length = 50)
    @Builder.Default
    private VirusScanStatus virusScanStatus = VirusScanStatus.CLEAN;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (classification == null) {
            classification = DocumentClassification.CONFIDENTIAL;
        }
        if (virusScanStatus == null) {
            virusScanStatus = VirusScanStatus.CLEAN;
        }
        if (version == null) {
            version = 1;
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
    }
}
