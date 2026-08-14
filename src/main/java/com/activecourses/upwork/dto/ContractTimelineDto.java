package com.activecourses.upwork.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractTimelineDto {
    private Integer contractId;
    private String contractTitle;
    private List<ContractTimelineEventDto> events;
}
