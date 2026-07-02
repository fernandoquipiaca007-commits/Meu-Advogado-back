-- V12__Extend_proposals.sql
-- Extends proposals table with legal-specific fields for LegalWork platform.

ALTER TABLE proposals
    ADD COLUMN IF NOT EXISTS proposed_duration INTEGER,
    ADD COLUMN IF NOT EXISTS strategy         TEXT,
    ADD COLUMN IF NOT EXISTS total_value      NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS updated_at       TIMESTAMP;

ALTER TABLE proposals DROP CONSTRAINT IF EXISTS proposals_status_check;

ALTER TABLE proposals
    ADD CONSTRAINT proposals_status_check
        CHECK (status IN ('Pending', 'Accepted', 'Rejected', 'Countered', 'Withdrawn'));

CREATE INDEX IF NOT EXISTS idx_proposals_job ON proposals (job_id);
CREATE INDEX IF NOT EXISTS idx_proposals_lawyer ON proposals (freelancer_id);
CREATE INDEX IF NOT EXISTS idx_proposals_status ON proposals (status);
