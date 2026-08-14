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
@Table(name = "contract_signatures", indexes = {
        @Index(name = "idx_contract_signatures_contract", columnList = "contract_id"),
        @Index(name = "idx_contract_signatures_user", columnList = "user_id"),
        @Index(name = "idx_contract_signatures_hash", columnList = "hash_receipt")
})
public class ContractSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "signature_type", nullable = false, length = 50)
    @Builder.Default
    private String signatureType = "ACCEPTANCE";

    @Column(name = "terms_version", nullable = false, length = 50)
    @Builder.Default
    private String termsVersion = "v1.0";

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "hash_receipt", nullable = false, length = 128)
    private String hashReceipt;

    @Column(name = "signed_at", nullable = false)
    @Builder.Default
    private LocalDateTime signedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (signedAt == null) {
            signedAt = LocalDateTime.now();
        }
        if (signatureType == null) {
            signatureType = "ACCEPTANCE";
        }
        if (termsVersion == null) {
            termsVersion = "v1.0";
        }
    }
}
