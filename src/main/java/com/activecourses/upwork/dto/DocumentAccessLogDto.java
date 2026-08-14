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
public class DocumentAccessLogDto {
    private Long id;
    private Long documentId;
    private Integer userId;
    private String userName;
    private String action;
    private LocalDateTime timestamp;
    private String ipAddress;
    private String userAgent;
}
