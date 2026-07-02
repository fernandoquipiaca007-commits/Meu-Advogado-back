package com.activecourses.upwork.repository.notification;

import com.activecourses.upwork.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(int userId);
    Page<Notification> findByUserIdOrderByCreatedAtDesc(int userId, Pageable pageable);
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(int userId);
    long countByUserIdAndIsReadFalse(int userId);
    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(int userId, com.activecourses.upwork.model.NotificationType type);
}
