-- V11__Extend_jobs_for_legal_cases.sql
-- Transforms the jobs table into legal case management for LegalWork.

-- Add new columns for legal case management
ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS urgency         VARCHAR(10)  NOT NULL DEFAULT 'Medium',
    ADD COLUMN IF NOT EXISTS confidentiality VARCHAR(20)  NOT NULL DEFAULT 'Public',
    ADD COLUMN IF NOT EXISTS estimated_value NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS deadline        DATE,
    ADD COLUMN IF NOT EXISTS specialty_id    INTEGER REFERENCES specialties (id),
    ADD COLUMN IF NOT EXISTS archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS archived_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS closed_at       TIMESTAMP;

-- Remove old CHECK constraint and add new ones
ALTER TABLE jobs DROP CONSTRAINT IF EXISTS jobs_job_type_check;

ALTER TABLE jobs
    ADD CONSTRAINT jobs_job_type_check
        CHECK (job_type IN ('Hourly', 'Fixed', 'ProBono', 'Contingency'));

ALTER TABLE jobs DROP CONSTRAINT IF EXISTS jobs_status_check;

ALTER TABLE jobs
    ADD CONSTRAINT jobs_status_check
        CHECK (status IN ('Open', 'InProposal', 'InProgress', 'Completed', 'Archived'));

-- Indexes for filtering
CREATE INDEX IF NOT EXISTS idx_jobs_urgency ON jobs (urgency);
CREATE INDEX IF NOT EXISTS idx_jobs_confidentiality ON jobs (confidentiality);
CREATE INDEX IF NOT EXISTS idx_jobs_specialty ON jobs (specialty_id);
CREATE INDEX IF NOT EXISTS idx_jobs_archived ON jobs (archived);
