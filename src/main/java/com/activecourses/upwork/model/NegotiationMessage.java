package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "negotiation_messages", indexes = {
    @Index(name = "idx_negotiation_messages_thread", columnList = "thread_id"),
    @Index(name = "idx_negotiation_messages_sender", columnList = "sender_id")
})
public class NegotiationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private NegotiationThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentMasked;

    @Column(columnDefinition = "TEXT")
    private String originalContent;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private boolean isModerated = false;

    @Column(length = 255)
    private String flaggedReason;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}
