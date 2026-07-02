package com.activecourses.upwork.repository.auth;

import com.activecourses.upwork.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Integer userId);
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);
    List<AuditLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);
}
