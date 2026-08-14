package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.JobStatus;
import com.activecourses.upwork.model.JobType;
import com.activecourses.upwork.model.JobVisibility;
import com.activecourses.upwork.model.UrgencyLevel;
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
public class JobDiscoveryDto {
    private Integer jobId;
    private String title;
    private String summary;
    private String description;
    private Integer specialtyId;
    private String specialtyName;
    private String specialty;
    private UrgencyLevel urgency;
    private BigDecimal budget;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private JobType jobType;
    private JobType budgetType;
    private BigDecimal estimatedValue;
    private String locationCity;
    private String locationState;
    private LocalDateTime createdAt;
    private JobVisibility visibility;
    private JobStatus status;
    private Set<String> skillNames;
    private Integer proposalsCount;
    private LocalDate deadline;

    public Integer getId() {
        return jobId;
    }

    public void setId(Integer id) {
        this.jobId = id;
    }
}
