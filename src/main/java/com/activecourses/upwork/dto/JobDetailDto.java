package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailDto {
    private Integer jobId;
    private String title;
    private String description;
    private BigDecimal budget;
    private JobType jobType;
    private JobStatus status;
    private UrgencyLevel urgency;
    private ConfidentialityLevel confidentiality;
    private JobVisibility visibility;
    private JobSensitivity sensitivity;
    private ModerationStatus moderationStatus;
    private String moderationReason;
    private BigDecimal estimatedValue;
    private LocalDate deadline;
    private Integer specialtyId;
    private String specialtyName;
    private Integer clientId;
    private String clientName;
    private Set<String> skillNames;
    private Integer proposalsCount;
    private LocalDateTime createdAt;
    private boolean isOwner;
    private boolean canPropose;

    public Integer getId() {
        return jobId;
    }

    public void setId(Integer id) {
        this.jobId = id;
    }
}
