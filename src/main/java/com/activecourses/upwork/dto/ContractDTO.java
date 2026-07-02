package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.ContractStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractDTO {
    private Integer contractId;
    private Integer jobId;
    private String jobTitle;
    private Integer clientId;
    private String clientName;
    private Integer lawyerId;
    private String lawyerName;
    private String lawyerPhotoUrl;
    private String lawyerOab;
    private Integer proposalId;
    private String title;
    private String description;
    private BigDecimal totalValue;
    private LocalDate startDate;
    private LocalDate endDate;
    private ContractStatus status;
    private LocalDateTime createdAt;
    private List<ContractMilestoneDTO> milestones;
}
