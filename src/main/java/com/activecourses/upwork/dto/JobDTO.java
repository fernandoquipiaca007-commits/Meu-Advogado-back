package com.activecourses.upwork.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import com.activecourses.upwork.model.*;

import lombok.Data;

@Data
public class JobDTO {
    private String title;
    private String description;
    private BigDecimal budget;
    private JobType jobType;
    private Set<Integer> skillIds;

    // Legal case fields
    private UrgencyLevel urgency;
    private ConfidentialityLevel confidentiality;
    private BigDecimal estimatedValue;
    private LocalDate deadline;
    private Integer specialtyId;
    private String clientName;
    private Set<String> skillNames;
}