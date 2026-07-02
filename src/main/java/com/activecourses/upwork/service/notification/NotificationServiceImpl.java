package com.activecourses.upwork.service.notification;

import com.activecourses.upwork.dto.NotificationDTO;
import com.activecourses.upwork.model.Notification;
import com.activecourses.upwork.model.NotificationType;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.notification.NotificationRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Override
    @Transactional
    public NotificationDTO createNotification(int userId, NotificationType type, String title,
                                               String message, String referenceType, Integer referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();

        notification = notificationRepository.save(notification);
        return mapToDTO(notification);
    }

    @Override
    public List<NotificationDTO> getMyNotifications() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return Collections.emptyList();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<NotificationDTO> getMyNotificationsPaged(Pageable pageable) {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return Page.empty();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToDTO);
    }

    @Override
    public List<NotificationDTO> getUnreadNotifications() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return Collections.emptyList();
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return 0;
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(int notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return;
        List<Notification> unread = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private NotificationDTO mapToDTO(Notification notification) {
        return NotificationDTO.builder()
                .notificationId(notification.getNotificationId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
