-- V8: Phase 4 — Stripe Collection & Ledger Financial Infrastructure
-- Additive migration: no existing data or columns are deleted.

-- ────────────────────────────────────────────────────────────────────────────
-- 1. payment_intents — Stripe PaymentIntent tracking per milestone
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_intents (
    id                       BIGSERIAL PRIMARY KEY,
    contract_id              INTEGER       NOT NULL REFERENCES contracts(contract_id) ON DELETE RESTRICT,
    milestone_id             INTEGER       REFERENCES contract_milestones(milestone_id) ON DELETE SET NULL,
    stripe_payment_intent_id VARCHAR(255)  UNIQUE,
    amount                   DECIMAL(15,2) NOT NULL,
    currency                 VARCHAR(10)   NOT NULL DEFAULT 'BRL',
    status                   VARCHAR(50)   NOT NULL DEFAULT 'PENDING_FUNDING',
    idempotency_key          VARCHAR(255)  NOT NULL UNIQUE,
    client_id                INTEGER       NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    metadata                 TEXT,
    created_at               TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_intents_contract     ON payment_intents(contract_id);
CREATE INDEX IF NOT EXISTS idx_payment_intents_milestone    ON payment_intents(milestone_id);
CREATE INDEX IF NOT EXISTS idx_payment_intents_stripe_id    ON payment_intents(stripe_payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_payment_intents_status       ON payment_intents(status);
CREATE INDEX IF NOT EXISTS idx_payment_intents_idempotency  ON payment_intents(idempotency_key);

-- ────────────────────────────────────────────────────────────────────────────
-- 2. ledger_entries — Double-entry append-only financial ledger
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ledger_entries (
    id                  BIGSERIAL PRIMARY KEY,
    payment_intent_id   BIGINT        REFERENCES payment_intents(id) ON DELETE RESTRICT,
    entry_type          VARCHAR(100)  NOT NULL,
    direction           VARCHAR(10)   NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount              DECIMAL(15,2) NOT NULL,
    currency            VARCHAR(10)   NOT NULL DEFAULT 'BRL',
    status              VARCHAR(50)   NOT NULL DEFAULT 'CONFIRMED',
    source              VARCHAR(100),
    provider_reference  VARCHAR(255),
    correlation_id      VARCHAR(255),
    actor_id            INTEGER       REFERENCES users(user_id) ON DELETE SET NULL,
    occurred_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    reverses_entry_id   BIGINT        REFERENCES ledger_entries(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_ledger_payment_intent   ON ledger_entries(payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_ledger_correlation      ON ledger_entries(correlation_id);
CREATE INDEX IF NOT EXISTS idx_ledger_provider_ref     ON ledger_entries(provider_reference);
CREATE INDEX IF NOT EXISTS idx_ledger_entry_type       ON ledger_entries(entry_type);
CREATE INDEX IF NOT EXISTS idx_ledger_occurred_at      ON ledger_entries(occurred_at);

-- ────────────────────────────────────────────────────────────────────────────
-- 3. refunds — Stripe refund records (append-only, never delete)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refunds (
    id                  BIGSERIAL PRIMARY KEY,
    payment_intent_id   BIGINT        NOT NULL REFERENCES payment_intents(id) ON DELETE RESTRICT,
    stripe_charge_id    VARCHAR(255),
    stripe_refund_id    VARCHAR(255)  UNIQUE,
    amount              DECIMAL(15,2) NOT NULL,
    reason              VARCHAR(255),
    status              VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    idempotency_key     VARCHAR(255)  NOT NULL UNIQUE,
    approved_by         INTEGER       REFERENCES users(user_id) ON DELETE SET NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment_intent  ON refunds(payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_refunds_stripe_refund   ON refunds(stripe_refund_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 4. provider_events — Idempotent Stripe webhook event store
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS provider_events (
    id                  BIGSERIAL PRIMARY KEY,
    provider            VARCHAR(50)   NOT NULL DEFAULT 'STRIPE',
    provider_event_id   VARCHAR(255)  NOT NULL UNIQUE,
    event_type          VARCHAR(100)  NOT NULL,
    payload_encrypted   TEXT,
    signature_valid     BOOLEAN       NOT NULL DEFAULT FALSE,
    processed           BOOLEAN       NOT NULL DEFAULT FALSE,
    processing_error    TEXT,
    retry_count         INTEGER       NOT NULL DEFAULT 0,
    received_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_provider_events_provider_id  ON provider_events(provider_event_id);
CREATE INDEX IF NOT EXISTS idx_provider_events_processed    ON provider_events(processed);
CREATE INDEX IF NOT EXISTS idx_provider_events_event_type   ON provider_events(event_type);

-- ────────────────────────────────────────────────────────────────────────────
-- 5. financial_feature_flags — Database-level feature flags for financial features
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS financial_feature_flags (
    id          BIGSERIAL PRIMARY KEY,
    feature_key VARCHAR(100) NOT NULL UNIQUE,
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    country     VARCHAR(10),
    modality    VARCHAR(50),
    owner       VARCHAR(100),
    valid_until TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO financial_feature_flags (feature_key, enabled, owner) VALUES
    ('stripe_collection_enabled', FALSE, 'PLATFORM'),
    ('paypal_payout_enabled',     FALSE, 'PLATFORM'),
    ('funds_hold_enabled',        FALSE, 'PLATFORM'),
    ('auto_release_enabled',      FALSE, 'PLATFORM')
ON CONFLICT (feature_key) DO NOTHING;
