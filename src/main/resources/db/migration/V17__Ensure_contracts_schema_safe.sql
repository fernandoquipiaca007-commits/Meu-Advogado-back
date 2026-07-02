-- V17__Ensure_contracts_schema_safe.sql
-- Safe variant of V13 without DROP TABLE.
-- Uses CREATE TABLE IF NOT EXISTS and ALTER TABLE ADD COLUMN IF NOT EXISTS
-- to ensure the schema is correct without risking data loss.

-- --- CONTRACTS ---
CREATE TABLE IF NOT EXISTS contracts
(
    contract_id    SERIAL PRIMARY KEY,
    job_id         INTEGER NOT NULL REFERENCES jobs (job_id),
    client_id      INTEGER NOT NULL REFERENCES users (id),
    lawyer_id      INTEGER NOT NULL REFERENCES users (id),
    proposal_id    INTEGER UNIQUE REFERENCES proposals (proposal_id),
    title          VARCHAR(255) NOT NULL,
    description    TEXT,
    total_value    NUMERIC(12, 2),
    start_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    end_date       DATE,
    status         VARCHAR(20) NOT NULL DEFAULT 'Active',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP
);

-- Add missing columns if contracts table already existed
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS total_value NUMERIC(12, 2);
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Ensure CHECK constraint exists (PostgreSQL compatible)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'contracts_status_check'
    ) THEN
        ALTER TABLE contracts ADD CONSTRAINT contracts_status_check
            CHECK (status IN ('Active', 'Completed', 'Terminated', 'Cancelled'));
    END IF;
END $$;

-- Create indexes if they don't exist
CREATE INDEX IF NOT EXISTS idx_contracts_job ON contracts (job_id);
CREATE INDEX IF NOT EXISTS idx_contracts_client ON contracts (client_id);
CREATE INDEX IF NOT EXISTS idx_contracts_lawyer ON contracts (lawyer_id);
CREATE INDEX IF NOT EXISTS idx_contracts_status ON contracts (status);

-- --- CONTRACT MILESTONES ---
CREATE TABLE IF NOT EXISTS contract_milestones
(
    milestone_id   SERIAL PRIMARY KEY,
    contract_id    INTEGER NOT NULL REFERENCES contracts (contract_id) ON DELETE CASCADE,
    title          VARCHAR(255) NOT NULL,
    description    TEXT,
    amount         NUMERIC(12, 2),
    due_date       DATE,
    status         VARCHAR(20) NOT NULL DEFAULT 'Pending',
    completed_at   TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE contract_milestones ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE contract_milestones ADD COLUMN IF NOT EXISTS amount NUMERIC(12, 2);
ALTER TABLE contract_milestones ADD COLUMN IF NOT EXISTS due_date DATE;
ALTER TABLE contract_milestones ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'contract_milestones_status_check'
    ) THEN
        ALTER TABLE contract_milestones ADD CONSTRAINT contract_milestones_status_check
            CHECK (status IN ('Pending', 'InProgress', 'Completed', 'Cancelled'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_milestones_contract ON contract_milestones (contract_id);

-- --- PAYMENTS ---
CREATE TABLE IF NOT EXISTS payments
(
    payment_id   SERIAL PRIMARY KEY,
    contract_id  INTEGER NOT NULL REFERENCES contracts (contract_id),
    milestone_id INTEGER REFERENCES contract_milestones (milestone_id),
    amount       NUMERIC(12, 2) NOT NULL,
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(10) NOT NULL DEFAULT 'Pending',
    description  VARCHAR(255)
);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS milestone_id INTEGER REFERENCES contract_milestones (milestone_id);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS description VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'payments_status_check'
    ) THEN
        ALTER TABLE payments ADD CONSTRAINT payments_status_check
            CHECK (status IN ('Pending', 'Completed', 'Failed', 'Refunded'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_payments_contract ON payments (contract_id);
CREATE INDEX IF NOT EXISTS idx_payments_milestone ON payments (milestone_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);

-- --- REVIEWS ---
CREATE TABLE IF NOT EXISTS reviews
(
    review_id   SERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts (contract_id),
    reviewer_id INTEGER NOT NULL REFERENCES users (id),
    reviewee_id INTEGER NOT NULL REFERENCES users (id),
    rating      INTEGER,
    comment     TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE reviews ADD COLUMN IF NOT EXISTS comment TEXT;
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'reviews_rating_check'
    ) THEN
        ALTER TABLE reviews ADD CONSTRAINT reviews_rating_check
            CHECK (rating >= 1 AND rating <= 5);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_reviews_contract ON reviews (contract_id);
CREATE INDEX IF NOT EXISTS idx_reviews_reviewer ON reviews (reviewer_id);
CREATE INDEX IF NOT EXISTS idx_reviews_reviewee ON reviews (reviewee_id);
