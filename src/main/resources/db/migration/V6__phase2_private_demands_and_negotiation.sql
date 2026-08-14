-- ============================================================
-- V6: Phase 2 — Demanda Privada, Descoberta Sanitizada e Negociação
-- ============================================================

-- 1. Jobs: Adicionar colunas de visibilidade, sensibilidade e moderação
DO $$ BEGIN
    ALTER TABLE jobs ADD COLUMN IF NOT EXISTS visibility VARCHAR(50) NOT NULL DEFAULT 'PRIVATE';
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE jobs ADD COLUMN IF NOT EXISTS sensitivity VARCHAR(50) NOT NULL DEFAULT 'STANDARD';
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE jobs ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW';
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE jobs ADD COLUMN IF NOT EXISTS moderation_reason TEXT;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

-- Índices para jobs
CREATE INDEX IF NOT EXISTS idx_jobs_visibility ON jobs(visibility);
CREATE INDEX IF NOT EXISTS idx_jobs_moderation_status ON jobs(moderation_status);
CREATE INDEX IF NOT EXISTS idx_jobs_discovery ON jobs(visibility, moderation_status, archived, status, specialty_id, urgency);

-- 2. Proposals: Adicionar versão da proposta e referência à negociação
DO $$ BEGIN
    ALTER TABLE proposals ADD COLUMN IF NOT EXISTS proposal_version INTEGER NOT NULL DEFAULT 1;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE proposals ADD COLUMN IF NOT EXISTS negotiation_thread_id BIGINT;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_proposals_negotiation_thread ON proposals(negotiation_thread_id);

-- Constraint de unicidade: 1 proposta ativa por advogado por demanda
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_proposal_per_job
ON proposals(job_id, freelancer_id)
WHERE status IN ('Pending', 'Countered', 'PENDING', 'COUNTERED', 'SUBMITTED', 'UNDER_REVIEW');

-- 3. Negotiation Threads (tópicos de negociação pré-contratual)
CREATE TABLE IF NOT EXISTS negotiation_threads (
    id BIGSERIAL PRIMARY KEY,
    proposal_id INTEGER NOT NULL UNIQUE REFERENCES proposals(proposal_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMP,
    retention_days INTEGER NOT NULL DEFAULT 90
);

CREATE INDEX IF NOT EXISTS idx_negotiation_threads_proposal ON negotiation_threads(proposal_id);

-- 4. Negotiation Messages (mensagens mascaradas do chat pré-contratual)
CREATE TABLE IF NOT EXISTS negotiation_messages (
    id BIGSERIAL PRIMARY KEY,
    thread_id BIGINT NOT NULL REFERENCES negotiation_threads(id) ON DELETE CASCADE,
    sender_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_masked TEXT NOT NULL,
    original_content TEXT,
    sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_moderated BOOLEAN NOT NULL DEFAULT FALSE,
    flagged_reason VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_negotiation_messages_thread ON negotiation_messages(thread_id);
CREATE INDEX IF NOT EXISTS idx_negotiation_messages_sender ON negotiation_messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_negotiation_messages_thread_sent ON negotiation_messages(thread_id, sent_at ASC);

-- 5. Retrocompatibilidade: Migrar jobs legados existentes para DISCOVERY_SANITIZED e APPROVED
UPDATE jobs
SET visibility = 'DISCOVERY_SANITIZED',
    moderation_status = 'APPROVED'
WHERE visibility IS NULL OR visibility = 'PRIVATE';
