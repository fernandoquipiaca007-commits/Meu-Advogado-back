-- ============================================================
-- V5: Phase 1 — Identidade, Autorização e Fundação de Dados
-- ============================================================

-- 1. User Profiles: Add verification status, OAB expiry, jurisdictions, MFA
DO $$ BEGIN
    ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS oab_expiry_date DATE;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS jurisdiction_states TEXT;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

-- Normalize existing verification_status values and set default to DRAFT
UPDATE user_profiles
SET verification_status = 'DRAFT'
WHERE verification_status IS NULL
   OR verification_status = 'unverified'
   OR verification_status = 'pending';

UPDATE user_profiles
SET verification_status = 'VERIFIED'
WHERE verification_status = 'verified';

ALTER TABLE user_profiles ALTER COLUMN verification_status SET DEFAULT 'DRAFT';

-- Index for verification_status
CREATE INDEX IF NOT EXISTS idx_user_profiles_verification_status ON user_profiles(verification_status);

-- 2. Law Firm Members table
CREATE TABLE IF NOT EXISTS law_firm_members (
    id SERIAL PRIMARY KEY,
    firm_id INTEGER REFERENCES law_firms(id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    role_in_firm VARCHAR(50) DEFAULT 'ASSOCIATE',
    is_responsible_lawyer BOOLEAN DEFAULT FALSE,
    is_partner BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(firm_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_law_firm_members_firm ON law_firm_members(firm_id);
CREATE INDEX IF NOT EXISTS idx_law_firm_members_user ON law_firm_members(user_id);

-- 3. Lawyer Firms table (junction table for legacy compatibility)
CREATE TABLE IF NOT EXISTS lawyer_firms (
    lawyer_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    firm_id INTEGER REFERENCES law_firms(id) ON DELETE CASCADE,
    is_partner BOOLEAN DEFAULT FALSE,
    PRIMARY KEY(lawyer_id, firm_id)
);
CREATE INDEX IF NOT EXISTS idx_lawyer_firms_lawyer ON lawyer_firms(lawyer_id);
CREATE INDEX IF NOT EXISTS idx_lawyer_firms_firm ON lawyer_firms(firm_id);

-- 4. Admin Access Logs table (immutable audit trail for privileged access)
CREATE TABLE IF NOT EXISTS admin_access_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    target_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    target_resource_type VARCHAR(100) NOT NULL,
    target_resource_id VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    justification TEXT NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_admin_access_logs_admin ON admin_access_logs(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_admin_access_logs_target ON admin_access_logs(target_user_id);
CREATE INDEX IF NOT EXISTS idx_admin_access_logs_created ON admin_access_logs(created_at DESC);
