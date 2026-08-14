package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.ConflictStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictCheckDto {
    private Long id;
    private Integer jobId;
    private Integer lawyerId;
    private String lawyerName;
    private ConflictStatus status;
    private String reasonMasked;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
