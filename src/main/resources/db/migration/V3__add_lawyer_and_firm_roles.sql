-- ============================================================
-- V3: Add ROLE_LAWYER and ROLE_FIRM to roles table
-- Ensures role-based authorization for legal professionals
-- ============================================================

INSERT INTO roles (name, created_at)
VALUES 
    ('ROLE_LAWYER', NOW()),
    ('ROLE_FIRM', NOW()),
    ('ROLE_CLIENT', NOW()),
    ('ROLE_ADMIN', NOW())
ON CONFLICT (name) DO NOTHING;
