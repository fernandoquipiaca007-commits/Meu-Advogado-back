-- V11: Phase 7 — Security, Observability & Controlled Launch
-- Additive migration: no existing data or columns deleted.

-- ────────────────────────────────────────────────────────────────────────────
-- 1. security_alerts — Alerts for suspicious activity, auth failures, etc.
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS security_alerts (
    id              BIGSERIAL PRIMARY KEY,
    alert_type      VARCHAR(100) NOT NULL,  -- SUSPICIOUS_LOGIN, IDOR_ATTEMPT, RATE_LIMIT_EXCEEDED, etc.
    severity        VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    actor_id        INTEGER      REFERENCES users(user_id) ON DELETE SET NULL,
    ip_address      VARCHAR(50),
    endpoint        VARCHAR(500),
    details         TEXT,
    resolved        BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved_by     INTEGER      REFERENCES users(user_id) ON DELETE SET NULL,
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_security_alerts_type     ON security_alerts(alert_type);
CREATE INDEX IF NOT EXISTS idx_security_alerts_severity ON security_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_security_alerts_resolved ON security_alerts(resolved);
CREATE INDEX IF NOT EXISTS idx_security_alerts_created  ON security_alerts(created_at);

-- ────────────────────────────────────────────────────────────────────────────
-- 2. system_health_checks — Automated health check results for monitoring
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_health_checks (
    id              BIGSERIAL PRIMARY KEY,
    check_name      VARCHAR(100) NOT NULL,  -- LEDGER_BALANCE, WEBHOOK_LAG, PAYOUT_STUCK, etc.
    status          VARCHAR(20)  NOT NULL DEFAULT 'OK',
    details         TEXT,
    checked_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_health_status CHECK (status IN ('OK', 'WARNING', 'CRITICAL', 'UNKNOWN'))
);

CREATE INDEX IF NOT EXISTS idx_health_checks_name    ON system_health_checks(check_name);
CREATE INDEX IF NOT EXISTS idx_health_checks_status  ON system_health_checks(status);
CREATE INDEX IF NOT EXISTS idx_health_checks_checked ON system_health_checks(checked_at);

-- ────────────────────────────────────────────────────────────────────────────
-- 3. launch_flags — Controlled rollout by country/modality/cohort
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS launch_flags (
    id              BIGSERIAL PRIMARY KEY,
    flag_key        VARCHAR(100) NOT NULL UNIQUE,
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    country         VARCHAR(10),
    modality        VARCHAR(50),
    cohort_size     INTEGER,
    rollback_notes  TEXT,
    approved_by     VARCHAR(255),
    approved_at     TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO launch_flags (flag_key, enabled, rollback_notes) VALUES
    ('production_launch', FALSE, 'Requires: legal review, Stripe sandbox validated, PayPal approved, security audit passed'),
    ('e2e_sandbox_mode',  TRUE,  'Sandbox mode active — no real credentials or financial operations'),
    ('admin_console',     FALSE, 'Financial reconciliation console — dual approval required')
ON CONFLICT (flag_key) DO NOTHING;
