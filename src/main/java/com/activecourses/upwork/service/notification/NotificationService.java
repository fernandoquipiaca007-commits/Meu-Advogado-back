package com.activecourses.upwork.service.notification;

import com.activecourses.upwork.dto.NotificationDTO;
import com.activecourses.upwork.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {
    NotificationDTO createNotification(int userId, NotificationType type, String title, String message, String referenceType, Integer referenceId);
    List<NotificationDTO> getMyNotifications();
    Page<NotificationDTO> getMyNotificationsPaged(Pageable pageable);
    List<NotificationDTO> getUnreadNotifications();
    long getUnreadCount();
    void markAsRead(int notificationId);
    void markAllAsRead();
}
