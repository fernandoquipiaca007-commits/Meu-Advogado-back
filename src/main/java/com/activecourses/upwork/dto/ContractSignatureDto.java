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
public class ContractSignatureDto {
    private Long id;
    private Integer contractId;
    private Integer userId;
    private String userName;
    private String signatureType;
    private String termsVersion;
    private String ipAddress;
    private String userAgent;
    private String hashReceipt;
    private LocalDateTime signedAt;
}
