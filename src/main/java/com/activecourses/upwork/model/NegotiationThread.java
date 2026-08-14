package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "negotiation_threads", indexes = {
    @Index(name = "idx_negotiation_threads_proposal", columnList = "proposal_id")
})
public class NegotiationThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false, unique = true)
    private Proposal proposal;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime closedAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer retentionDays = 90;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NegotiationMessage> messages = new ArrayList<>();
}
