package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_pi",         columnList = "payment_intent_id"),
    @Index(name = "idx_ledger_correlation", columnList = "correlation_id"),
    @Index(name = "idx_ledger_provider",    columnList = "provider_reference"),
    @Index(name = "idx_ledger_type",        columnList = "entry_type")
})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_intent_id")
    private PaymentIntent paymentIntent;

    @Column(name = "entry_type", nullable = false, length = 100)
    private String entryType;

    @Column(nullable = false, length = 10)
    private String direction; // DEBIT | CREDIT

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "BRL";

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "CONFIRMED";

    @Column(length = 100)
    private String source;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Builder.Default
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_entry_id")
    private LedgerEntry reversesEntry;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }
}
