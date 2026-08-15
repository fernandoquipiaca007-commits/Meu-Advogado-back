-- V10: Phase 6 — Delivery, Cancellation, Dispute & Reputation
-- Additive migration: no existing data or columns are deleted.

-- ────────────────────────────────────────────────────────────────────────────
-- 1. deliveries — Formal delivery submissions by lawyer per milestone
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS deliveries (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         INTEGER       NOT NULL REFERENCES contracts(contract_id) ON DELETE RESTRICT,
    milestone_id        INTEGER       REFERENCES contract_milestones(milestone_id) ON DELETE RESTRICT,
    submitted_by        INTEGER       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    version             INTEGER       NOT NULL DEFAULT 1,
    -- Status lifecycle: SUBMITTED -> CHANGE_REQUESTED | ACCEPTED
    status              VARCHAR(50)   NOT NULL DEFAULT 'SUBMITTED',
    description         TEXT          NOT NULL,
    criteria_satisfied  TEXT,
    limitations_noted   TEXT,
    -- Client receipt tracking
    client_viewed_at    TIMESTAMP,
    change_request_reason TEXT,
    accepted_at         TIMESTAMP,
    accepted_by         INTEGER       REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_delivery_status CHECK (
        status IN ('SUBMITTED', 'CHANGE_REQUESTED', 'ACCEPTED', 'SUPERSEDED')
    )
);

CREATE INDEX IF NOT EXISTS idx_deliveries_contract   ON deliveries(contract_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_milestone  ON deliveries(milestone_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_status     ON deliveries(status);

-- ────────────────────────────────────────────────────────────────────────────
-- 2. contract_addenda — Versioned scope changes (not silent modifications)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS contract_addenda (
    id              BIGSERIAL PRIMARY KEY,
    contract_id     INTEGER     NOT NULL REFERENCES contracts(contract_id) ON DELETE RESTRICT,
    version         INTEGER     NOT NULL DEFAULT 1,
    proposed_by     INTEGER     NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    description     TEXT        NOT NULL,
    changes_summary TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reviewed_by     INTEGER     REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at     TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_addendum_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_addenda_contract ON contract_addenda(contract_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 3. cancellation_requests — Negotiated cancellation (no silent acceptance)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cancellation_requests (
    id                      BIGSERIAL PRIMARY KEY,
    contract_id             INTEGER     NOT NULL REFERENCES contracts(contract_id) ON DELETE RESTRICT,
    initiated_by            INTEGER     NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    -- Categorized reason: CLIENT_REQUEST, LAWYER_WITHDRAWAL, MUTUAL_AGREEMENT, BREACH, FORCE_MAJEURE
    reason_category         VARCHAR(100) NOT NULL,
    reason_detail           TEXT,
    -- Financial split proposal (percentages)
    proposed_client_pct     DECIMAL(5,2),
    proposed_lawyer_pct     DECIMAL(5,2),
    -- Deliveries, documents, access handoff checklist
    deliveries_handoff_done BOOLEAN     NOT NULL DEFAULT FALSE,
    documents_handoff_done  BOOLEAN     NOT NULL DEFAULT FALSE,
    access_revoked          BOOLEAN     NOT NULL DEFAULT FALSE,
    deadline_alert_sent     BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Response from counterpart
    counterpart_response    VARCHAR(50),
    counterpart_note        TEXT,
    counterpart_responded_at TIMESTAMP,
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    resolved_at             TIMESTAMP,
    created_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_cancel_status CHECK (
        status IN ('PENDING', 'NEGOTIATING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'ESCALATED')
    ),
    CONSTRAINT chk_counterpart_response CHECK (
        counterpart_response IS NULL OR counterpart_response IN ('ACCEPTED', 'REJECTED', 'COUNTER_PROPOSED')
    )
);

CREATE INDEX IF NOT EXISTS idx_cancel_contract ON cancellation_requests(contract_id);
CREATE INDEX IF NOT EXISTS idx_cancel_status   ON cancellation_requests(status);

-- ────────────────────────────────────────────────────────────────────────────
-- 4. lawyer_withdrawals — Lawyer resignation with mandatory handoff checklist
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS lawyer_withdrawals (
    id                      BIGSERIAL PRIMARY KEY,
    contract_id             INTEGER     NOT NULL REFERENCES contracts(contract_id) ON DELETE RESTRICT,
    lawyer_id               INTEGER     NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    urgency_checked         BOOLEAN     NOT NULL DEFAULT FALSE,
    deadline_checked        BOOLEAN     NOT NULL DEFAULT FALSE,
    handoff_completed       BOOLEAN     NOT NULL DEFAULT FALSE,
    escalation_triggered    BOOLEAN     NOT NULL DEFAULT FALSE,
    withdrawal_date         TIMESTAMP,
    access_restricted_at    TIMESTAMP,
    reason                  TEXT        NOT NULL,
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING_CHECKLIST',
    created_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_withdrawal_status CHECK (
        status IN ('PENDING_CHECKLIST', 'CONFIRMED', 'BLOCKED_URGENT', 'ESCALATED')
    )
);

CREATE INDEX IF NOT EXISTS idx_withdrawals_contract ON lawyer_withdrawals(contract_id);
CREATE INDEX IF NOT EXISTS idx_withdrawals_lawyer   ON lawyer_withdrawals(lawyer_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 5. disputes — Operational disputes (platform decides finance, NOT legal merit)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS disputes (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         INTEGER     NOT NULL REFERENCES contracts(contract_id) ON DELETE RESTRICT,
    opened_by           INTEGER     NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason_category     VARCHAR(100) NOT NULL,
    description         TEXT        NOT NULL,
    -- Evidence references (links to secure_documents or delivery records)
    evidence_summary    TEXT,
    -- Financial hold during dispute
    frozen_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    -- Decision
    status              VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    decision            VARCHAR(100),
    decision_reason     TEXT,
    decided_by          INTEGER     REFERENCES users(id) ON DELETE SET NULL,
    decided_at          TIMESTAMP,
    external_channel_url TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dispute_status CHECK (
        status IN ('OPEN', 'UNDER_REVIEW', 'PENDING_EVIDENCE', 'DECIDED', 'CLOSED', 'APPEALED')
    )
);

CREATE INDEX IF NOT EXISTS idx_disputes_contract ON disputes(contract_id);
CREATE INDEX IF NOT EXISTS idx_disputes_status   ON disputes(status);

-- ────────────────────────────────────────────────────────────────────────────
-- 6. reviews — Blind mutual evaluation with moderation
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reviews (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         INTEGER     NOT NULL REFERENCES contracts(contract_id) ON DELETE RESTRICT,
    reviewer_id         INTEGER     NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reviewee_id         INTEGER     NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    -- Score: 1-5, no automatic zero
    score               INTEGER     NOT NULL CHECK (score BETWEEN 1 AND 5),
    -- Structured dimensions (all optional)
    communication_score INTEGER     CHECK (communication_score BETWEEN 1 AND 5),
    quality_score       INTEGER     CHECK (quality_score BETWEEN 1 AND 5),
    timeliness_score    INTEGER     CHECK (timeliness_score BETWEEN 1 AND 5),
    comment             TEXT,
    -- Blind reveal control: review is hidden until BOTH submit or deadline passes
    is_revealed         BOOLEAN     NOT NULL DEFAULT FALSE,
    submitted_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    -- Moderation
    moderation_status   VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    moderation_note     TEXT,
    is_reported         BOOLEAN     NOT NULL DEFAULT FALSE,
    report_reason       TEXT,
    -- Visibility
    is_visible          BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_review_moderation CHECK (
        moderation_status IN ('PENDING', 'APPROVED', 'REJECTED', 'UNDER_REVIEW')
    ),
    -- One review per participant per contract
    UNIQUE (contract_id, reviewer_id)
);

CREATE INDEX IF NOT EXISTS idx_reviews_contract  ON reviews(contract_id);
CREATE INDEX IF NOT EXISTS idx_reviews_reviewee  ON reviews(reviewee_id);
CREATE INDEX IF NOT EXISTS idx_reviews_moderation ON reviews(moderation_status);
