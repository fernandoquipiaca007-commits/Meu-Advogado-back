-- V9: Phase 5 — PayPal Payouts: Lawyer Payout Infrastructure
-- Additive migration: no existing data or columns deleted.

-- ────────────────────────────────────────────────────────────────────────────
-- 1. payout_accounts — Lawyer's connected PayPal identity (tokenized, never raw)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payout_accounts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             INTEGER       NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE RESTRICT,
    provider            VARCHAR(50)   NOT NULL DEFAULT 'PAYPAL',
    -- PayPal Payer ID (preferred) or masked email — never store plaintext email as primary key
    paypal_payer_id     VARCHAR(255),
    paypal_email_masked VARCHAR(255),  -- e.g. "a***@example.com"
    -- Encrypted token (AES-256-GCM) for any stored credential
    encrypted_token     TEXT,
    status              VARCHAR(50)   NOT NULL DEFAULT 'PENDING_VALIDATION',
    validated_at        TIMESTAMP,
    revoked_at          TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payout_account_provider CHECK (provider IN ('PAYPAL'))
);

CREATE INDEX IF NOT EXISTS idx_payout_accounts_user   ON payout_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_payout_accounts_status ON payout_accounts(status);

-- ────────────────────────────────────────────────────────────────────────────
-- 2. payout_requests — Each request to pay the lawyer via PayPal Payouts API
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payout_requests (
    id                      BIGSERIAL PRIMARY KEY,
    lawyer_id               INTEGER       NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    payout_account_id       BIGINT        NOT NULL REFERENCES payout_accounts(id) ON DELETE RESTRICT,
    contract_id             INTEGER       REFERENCES contracts(contract_id) ON DELETE RESTRICT,
    ledger_entry_id         BIGINT        REFERENCES ledger_entries(id) ON DELETE RESTRICT,
    -- Amounts
    gross_amount            DECIMAL(15,2) NOT NULL,
    platform_fee            DECIMAL(15,2) NOT NULL DEFAULT 0,
    conversion_fee          DECIMAL(15,2) NOT NULL DEFAULT 0,
    net_amount              DECIMAL(15,2) NOT NULL,
    currency                VARCHAR(10)   NOT NULL DEFAULT 'BRL',
    -- PayPal Batch & Item IDs for idempotency
    sender_batch_id         VARCHAR(255)  NOT NULL UNIQUE,
    sender_item_id          VARCHAR(255)  NOT NULL UNIQUE,
    paypal_payout_batch_id  VARCHAR(255)  UNIQUE,
    paypal_payout_item_id   VARCHAR(255)  UNIQUE,
    -- Status tracking to terminal states
    status                  VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    failure_reason          VARCHAR(500),
    -- Approval chain (MFA + dual approval for manual operations)
    requested_by            INTEGER       REFERENCES users(user_id) ON DELETE SET NULL,
    approved_by             INTEGER       REFERENCES users(user_id) ON DELETE SET NULL,
    approved_at             TIMESTAMP,
    -- Timestamps
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMP,
    CONSTRAINT chk_payout_status CHECK (
        status IN ('PENDING', 'RESERVED', 'SUBMITTED', 'SUCCESS',
                   'UNCLAIMED', 'REFUNDED', 'FAILED', 'ON_HOLD',
                   'BLOCKED', 'DENIED', 'RETURNED', 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS idx_payout_requests_lawyer       ON payout_requests(lawyer_id);
CREATE INDEX IF NOT EXISTS idx_payout_requests_contract     ON payout_requests(contract_id);
CREATE INDEX IF NOT EXISTS idx_payout_requests_status       ON payout_requests(status);
CREATE INDEX IF NOT EXISTS idx_payout_requests_batch_id     ON payout_requests(sender_batch_id);
CREATE INDEX IF NOT EXISTS idx_payout_requests_paypal_batch ON payout_requests(paypal_payout_batch_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 3. payout_eligibility_snapshots — Audit trail of eligibility checks
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payout_eligibility_snapshots (
    id                  BIGSERIAL PRIMARY KEY,
    lawyer_id           INTEGER       NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    contract_id         INTEGER       REFERENCES contracts(contract_id) ON DELETE SET NULL,
    ledger_balance      DECIMAL(15,2) NOT NULL DEFAULT 0,
    held_balance        DECIMAL(15,2) NOT NULL DEFAULT 0,
    disputed_balance    DECIMAL(15,2) NOT NULL DEFAULT 0,
    available_to_payout DECIMAL(15,2) NOT NULL DEFAULT 0,
    has_active_dispute  BOOLEAN       NOT NULL DEFAULT FALSE,
    has_active_hold     BOOLEAN       NOT NULL DEFAULT FALSE,
    has_chargeback      BOOLEAN       NOT NULL DEFAULT FALSE,
    risk_hold_days      INTEGER       NOT NULL DEFAULT 0,
    eligible            BOOLEAN       NOT NULL DEFAULT FALSE,
    ineligibility_reasons TEXT,
    checked_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payout_eligibility_lawyer ON payout_eligibility_snapshots(lawyer_id);
CREATE INDEX IF NOT EXISTS idx_payout_eligibility_checked ON payout_eligibility_snapshots(checked_at);

-- ────────────────────────────────────────────────────────────────────────────
-- 4. paypal_webhook_events — Idempotent PayPal webhook/IPN event store
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS paypal_webhook_events (
    id                  BIGSERIAL PRIMARY KEY,
    paypal_event_id     VARCHAR(255)  NOT NULL UNIQUE,
    event_type          VARCHAR(100)  NOT NULL,
    resource_type       VARCHAR(100),
    resource_id         VARCHAR(255),
    payload_encrypted   TEXT,
    processed           BOOLEAN       NOT NULL DEFAULT FALSE,
    processing_error    TEXT,
    retry_count         INTEGER       NOT NULL DEFAULT 0,
    received_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_paypal_events_event_id   ON paypal_webhook_events(paypal_event_id);
CREATE INDEX IF NOT EXISTS idx_paypal_events_processed  ON paypal_webhook_events(processed);
CREATE INDEX IF NOT EXISTS idx_paypal_events_resource   ON paypal_webhook_events(resource_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 5. financial_feature_flags: add paypal entry (if not already there from V8)
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO financial_feature_flags (feature_key, enabled, owner) VALUES
    ('paypal_payout_enabled', FALSE, 'PLATFORM')
ON CONFLICT (feature_key) DO NOTHING;
