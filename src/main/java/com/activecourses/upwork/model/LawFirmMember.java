package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "law_firm_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"firm_id", "user_id"})
})
public class LawFirmMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "firm_id", nullable = false)
    private LawFirm firm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_in_firm", length = 50)
    @Builder.Default
    private FirmRole roleInFirm = FirmRole.ASSOCIATE;

    @Builder.Default
    @Column(name = "is_responsible_lawyer")
    private Boolean isResponsibleLawyer = false;

    @Builder.Default
    @Column(name = "is_partner")
    private Boolean isPartner = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
