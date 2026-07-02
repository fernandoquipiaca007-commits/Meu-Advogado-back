package com.activecourses.upwork.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractDocumentDTO {
    private Integer documentId;
    private Integer contractId;
    private Integer uploadedById;
    private String uploadedByName;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String description;
    private String category;
    private LocalDateTime createdAt;
}
