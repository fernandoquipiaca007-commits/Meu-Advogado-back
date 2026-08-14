package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payout_accounts", indexes = {
    @Index(name = "idx_pa_user",   columnList = "user_id"),
    @Index(name = "idx_pa_status", columnList = "status")
})
public class PayoutAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String provider = "PAYPAL";

    /** PayPal Payer ID — preferred over email */
    @Column(name = "paypal_payer_id", length = 255)
    private String paypalPayerId;

    /** Masked email for display only — e.g. "a***@example.com" */
    @Column(name = "paypal_email_masked", length = 255)
    private String paypalEmailMasked;

    /** AES-256-GCM encrypted credential token */
    @Column(name = "encrypted_token", columnDefinition = "TEXT")
    private String encryptedToken;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "PENDING_VALIDATION";

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
