package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.DocumentClassification;
import com.activecourses.upwork.model.VirusScanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureDocumentDto {
    private Long id;
    private Integer contractId;
    private Integer jobId;
    private Integer ownerId;
    private String ownerName;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String sha256Hash;
    private DocumentClassification classification;
    private VirusScanStatus virusScanStatus;
    private Integer version;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
