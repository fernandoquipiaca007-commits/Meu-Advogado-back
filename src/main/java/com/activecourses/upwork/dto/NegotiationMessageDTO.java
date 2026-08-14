package com.activecourses.upwork.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegotiationMessageDTO {
    private Long id;
    private Long threadId;
    private Integer proposalId;
    private Integer senderId;
    private String senderName;
    private String senderRole;
    private String contentMasked;
    private String originalContent;
    private LocalDateTime sentAt;
    private boolean isModerated;
    private String flaggedReason;
}
