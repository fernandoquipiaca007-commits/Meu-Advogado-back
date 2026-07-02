package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Integer notificationId;
    private NotificationType type;
    private String title;
    private String message;
    private String referenceType;
    private Integer referenceId;
    private boolean isRead;
    private LocalDateTime createdAt;
}
