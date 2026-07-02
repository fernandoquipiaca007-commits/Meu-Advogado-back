package com.activecourses.upwork.service.authentication;

import com.activecourses.upwork.model.AuditLog;
import com.activecourses.upwork.repository.auth.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    public void log(Integer userId, String action, String details, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .details(details)
                    .ipAddress(ipAddress)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
            logger.info("Audit: userId={}, action={}, details={}, ip={}", userId, action, details, ipAddress);
        } catch (Exception e) {
            logger.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    public void logLogin(Integer userId, String ipAddress) {
        log(userId, "LOGIN", "User logged in successfully", ipAddress);
    }

    public void logLoginFailed(String email, String ipAddress) {
        log(0, "LOGIN_FAILED", "Failed login attempt for email: " + email, ipAddress);
    }

    public void logLogout(Integer userId, String ipAddress) {
        log(userId, "LOGOUT", "User logged out", ipAddress);
    }

    public void logRegister(Integer userId, String ipAddress) {
        log(userId, "REGISTER", "User registered successfully", ipAddress);
    }

    public void logPasswordReset(Integer userId, String ipAddress) {
        log(userId, "PASSWORD_RESET", "Password reset successfully", ipAddress);
    }

    public void logPasswordResetRequest(String email, String ipAddress) {
        log(0, "PASSWORD_RESET_REQUEST", "Password reset requested for email: " + email, ipAddress);
    }
}
