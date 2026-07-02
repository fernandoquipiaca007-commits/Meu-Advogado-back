-- V13__Create_contracts_legal.sql
-- Extends contracts table for LegalWork mandates and creates milestones.

-- Drop existing contracts table and recreate with legal fields
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS reviews CASCADE;
DROP TABLE IF EXISTS contracts CASCADE;

CREATE TABLE contracts
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
    status         VARCHAR(20) NOT NULL DEFAULT 'Active'
                       CHECK (status IN ('Active', 'Completed', 'Terminated', 'Cancelled')),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE contract_milestones
(
    milestone_id   SERIAL PRIMARY KEY,
    contract_id    INTEGER NOT NULL REFERENCES contracts (contract_id) ON DELETE CASCADE,
    title          VARCHAR(255) NOT NULL,
    description    TEXT,
    amount         NUMERIC(12, 2),
    due_date       DATE,
    status         VARCHAR(20) NOT NULL DEFAULT 'Pending'
                       CHECK (status IN ('Pending', 'InProgress', 'Completed', 'Cancelled')),
    completed_at   TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_contracts_job ON contracts (job_id);
CREATE INDEX idx_contracts_client ON contracts (client_id);
CREATE INDEX idx_contracts_lawyer ON contracts (lawyer_id);
CREATE INDEX idx_contracts_status ON contracts (status);
CREATE INDEX idx_milestones_contract ON contract_milestones (contract_id);

-- Recreate payments and reviews with proper foreign keys
CREATE TABLE payments
(
    payment_id   SERIAL PRIMARY KEY,
    contract_id  INTEGER NOT NULL REFERENCES contracts (contract_id),
    milestone_id INTEGER REFERENCES contract_milestones (milestone_id),
    amount       NUMERIC(12, 2) NOT NULL,
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(10) NOT NULL DEFAULT 'Pending'
                     CHECK (status IN ('Pending', 'Completed', 'Failed', 'Refunded')),
    description  VARCHAR(255)
);

CREATE TABLE reviews
(
    review_id   SERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts (contract_id),
    reviewer_id INTEGER NOT NULL REFERENCES users (id),
    reviewee_id INTEGER NOT NULL REFERENCES users (id),
    rating      INTEGER CHECK (rating >= 1 AND rating <= 5),
    comment     TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
