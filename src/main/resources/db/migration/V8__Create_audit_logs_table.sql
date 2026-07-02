-- V8__Create_audit_logs_table.sql
-- Creates audit_logs table for tracking authentication and security events.

CREATE TABLE audit_logs
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    INTEGER      NOT NULL,
    action     VARCHAR(50)  NOT NULL,
    details    VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
