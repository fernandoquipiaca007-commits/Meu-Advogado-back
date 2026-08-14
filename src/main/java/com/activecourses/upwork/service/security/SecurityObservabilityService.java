package com.activecourses.upwork.service.security;

import com.activecourses.upwork.model.SecurityAlert;
import com.activecourses.upwork.model.User;
import com.activecourses.upwork.repository.security.SecurityAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Security Observability Service — Phase 7 production gate.
 *
 * Responsibilities:
 * - Record security alerts (IDOR attempts, auth failures, rate limit breaches, etc.)
 * - Provide health check views for monitoring dashboards
 * - Never expose sensitive data in alert payloads
 *
 * Alert types:
 * SUSPICIOUS_LOGIN, IDOR_ATTEMPT, RATE_LIMIT_EXCEEDED, UNAUTHORIZED_ACCESS,
 * WEBHOOK_SIGNATURE_INVALID, DUPLICATE_PAYOUT_ATTEMPT, ADMIN_ACCESS,
 * MODERATION_FLAG, UPLOAD_REJECTED, CONTRACT_STUCK, LEDGER_DIVERGENCE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityObservabilityService {

    private final SecurityAlertRepository securityAlertRepository;

    /**
     * Records a security alert. Severity: LOW | MEDIUM | HIGH | CRITICAL.
     */
    @Transactional
    public SecurityAlert recordAlert(String alertType, String severity, User actor,
                                      String ipAddress, String endpoint, String details) {
        SecurityAlert alert = SecurityAlert.builder()
                .alertType(alertType)
                .severity(severity != null ? severity : "MEDIUM")
                .actor(actor)
                .ipAddress(ipAddress)
                .endpoint(endpoint)
                .details(details)
                .resolved(false)
                .build();

        SecurityAlert saved = securityAlertRepository.save(alert);
        log.warn("[SECURITY_ALERT] type={} severity={} actor={} ip={} endpoint={}",
                alertType, severity,
                actor != null ? actor.getId() : "anonymous",
                ipAddress, endpoint);

        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            log.error("[SECURITY_ALERT] HIGH/CRITICAL alert requires immediate attention: type={} details={}",
                    alertType, details);
        }
        return saved;
    }

    /**
     * Convenience: record IDOR attempt (unauthorized resource access).
     */
    @Transactional
    public SecurityAlert recordIdorAttempt(User actor, String endpoint, String resourceInfo) {
        return recordAlert("IDOR_ATTEMPT", "HIGH", actor, null, endpoint,
                "Tentativa de acesso não autorizado a recurso: " + resourceInfo);
    }

    /**
     * Convenience: record invalid Stripe webhook signature.
     */
    @Transactional
    public SecurityAlert recordInvalidWebhookSignature(String ipAddress, String provider) {
        return recordAlert("WEBHOOK_SIGNATURE_INVALID", "HIGH", null, ipAddress,
                "/api/webhooks/" + provider.toLowerCase(),
                "Webhook recebido com assinatura inválida do provider: " + provider);
    }

    /**
     * Convenience: record ledger balance divergence.
     */
    @Transactional
    public SecurityAlert recordLedgerDivergence(Integer contractId, String details) {
        return recordAlert("LEDGER_DIVERGENCE", "CRITICAL", null, null,
                "/api/payments/ledger/" + contractId, details);
    }

    /**
     * Resolves an alert (marks as handled).
     */
    @Transactional
    public SecurityAlert resolveAlert(Long alertId, User resolver) {
        SecurityAlert alert = securityAlertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alerta não encontrado: " + alertId));
        alert.setResolved(true);
        alert.setResolvedBy(resolver);
        alert.setResolvedAt(LocalDateTime.now());
        SecurityAlert saved = securityAlertRepository.save(alert);
        log.info("[SECURITY_ALERT] Resolved id={} by={}", alertId, resolver.getId());
        return saved;
    }

    /**
     * Returns all unresolved CRITICAL and HIGH alerts for dashboard.
     */
    @Transactional(readOnly = true)
    public List<SecurityAlert> getCriticalAlerts() {
        List<SecurityAlert> critical = securityAlertRepository.findBySeverityAndResolvedFalse("CRITICAL");
        List<SecurityAlert> high     = securityAlertRepository.findBySeverityAndResolvedFalse("HIGH");
        critical.addAll(high);
        critical.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return critical;
    }

    /**
     * Returns health metrics summary for monitoring dashboards.
     */
    @Transactional(readOnly = true)
    public HealthSummary getHealthSummary() {
        long criticalCount = securityAlertRepository.countBySeverityAndResolvedFalse("CRITICAL");
        long highCount     = securityAlertRepository.countBySeverityAndResolvedFalse("HIGH");
        return new HealthSummary(criticalCount, highCount);
    }

    /** Simple health summary DTO */
    public record HealthSummary(long criticalAlerts, long highAlerts) {
        public boolean isHealthy() {
            return criticalAlerts == 0;
        }
    }
}
