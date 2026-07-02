package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.PaymentStatus;
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
public class PaymentDTO {
    private Integer paymentId;
    private Integer contractId;
    private String contractTitle;
    private Integer milestoneId;
    private String milestoneTitle;
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime paymentDate;
    private String description;
    private String clientName;
    private String lawyerName;
    private String lawyerOab;
}
