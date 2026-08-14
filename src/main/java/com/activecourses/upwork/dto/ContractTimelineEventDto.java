package com.activecourses.upwork.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractTimelineEventDto {
    private String id;
    private Integer contractId;
    private String eventType; // DEMAND_CREATED, PROPOSAL_SUBMITTED, CONFLICT_CLEARED, CONTRACT_SIGNED, MILESTONE_CREATED, MILESTONE_COMPLETED, DOCUMENT_ATTACHED
    private String title;
    private String description;
    private LocalDateTime timestamp;
    private Integer actorId;
    private String actorName;
    private String actorRole;
    private String status;
    private Map<String, Object> metadata;
}
