package com.activecourses.upwork.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "law_firms")
public class LawFirm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String cnpj;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String phone;

    private String website;

    private String logoUrl;

    private String address;

    private String city;

    private String state;

    @Builder.Default
    @Column(nullable = false)
    private String country = "BR";

    @Builder.Default
    @Column(nullable = false)
    private boolean isVerified = false;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
