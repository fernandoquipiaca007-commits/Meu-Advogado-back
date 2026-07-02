-- V10__Extend_user_profiles.sql
-- Adds legal-specific fields to user_profiles for LegalWork platform.

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS oab_number   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS oab_state    VARCHAR(2),
    ADD COLUMN IF NOT EXISTS country      VARCHAR(50)  NOT NULL DEFAULT 'BR',
    ADD COLUMN IF NOT EXISTS phone        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS photo_url    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS languages    TEXT,
    ADD COLUMN IF NOT EXISTS experience_years INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) NOT NULL DEFAULT 'unverified',
    ADD COLUMN IF NOT EXISTS client_type  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS company_name VARCHAR(255);

CREATE INDEX idx_user_profiles_oab ON user_profiles (oab_number);
CREATE INDEX idx_user_profiles_verification ON user_profiles (verification_status);
