package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.ProposalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalDTO {
    private Integer proposalId;
    private Integer jobId;
    private String jobTitle;
    private Integer lawyerId;
    private String lawyerName;
    private String coverLetter;
    private BigDecimal proposedRate;
    private ProposalStatus status;
    private LocalDateTime createdAt;

    // Legal-specific
    private Integer proposedDuration;
    private String strategy;
    private BigDecimal totalValue;
    private String lawyerPhotoUrl;
    private String lawyerOab;
    private Integer lawyerExperienceYears;
    private Integer contractId;
}
