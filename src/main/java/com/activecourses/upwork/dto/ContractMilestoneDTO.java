package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.MilestoneStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractMilestoneDTO {
    private Integer milestoneId;
    private Integer contractId;
    private String title;
    private String description;
    private BigDecimal amount;
    private LocalDate dueDate;
    private MilestoneStatus status;
    private LocalDateTime completedAt;
}
