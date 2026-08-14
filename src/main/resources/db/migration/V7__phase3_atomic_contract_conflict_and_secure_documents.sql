-- ============================================================
-- V7: Phase 3 — Contratação Atômica, Conflito de Interesses e Documentos Seguros
-- ============================================================

-- 1. Tabela conflict_checks (checagens de conflito de interesses)
CREATE TABLE IF NOT EXISTS conflict_checks (
    id BIGSERIAL PRIMARY KEY,
    job_id INTEGER NOT NULL REFERENCES jobs(job_id) ON DELETE CASCADE,
    lawyer_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    reason_masked VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP,
    CONSTRAINT uq_conflict_checks_job_lawyer UNIQUE (job_id, lawyer_id)
);

CREATE INDEX IF NOT EXISTS idx_conflict_checks_job ON conflict_checks(job_id);
CREATE INDEX IF NOT EXISTS idx_conflict_checks_lawyer ON conflict_checks(lawyer_id);
CREATE INDEX IF NOT EXISTS idx_conflict_checks_status ON conflict_checks(status);

-- 2. Tabela contract_signatures (assinaturas e recibos digitais dos termos de contratação)
CREATE TABLE IF NOT EXISTS contract_signatures (
    id BIGSERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts(contract_id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    signature_type VARCHAR(50) NOT NULL DEFAULT 'ACCEPTANCE',
    terms_version VARCHAR(50) NOT NULL DEFAULT 'v1.0',
    ip_address VARCHAR(100),
    user_agent VARCHAR(255),
    hash_receipt VARCHAR(128) NOT NULL,
    signed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_contract_signatures_contract ON contract_signatures(contract_id);
CREATE INDEX IF NOT EXISTS idx_contract_signatures_user ON contract_signatures(user_id);
CREATE INDEX IF NOT EXISTS idx_contract_signatures_hash ON contract_signatures(hash_receipt);

-- 3. Tabela secure_documents (cofre de documentos seguros com hash e antivírus)
CREATE TABLE IF NOT EXISTS secure_documents (
    id BIGSERIAL PRIMARY KEY,
    contract_id INTEGER REFERENCES contracts(contract_id) ON DELETE CASCADE,
    job_id INTEGER REFERENCES jobs(job_id) ON DELETE CASCADE,
    owner_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    sha256_hash VARCHAR(128) NOT NULL,
    classification VARCHAR(50) NOT NULL DEFAULT 'CONFIDENTIAL',
    virus_scan_status VARCHAR(50) NOT NULL DEFAULT 'CLEAN',
    version INTEGER NOT NULL DEFAULT 1,
    description TEXT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_secure_documents_contract ON secure_documents(contract_id);
CREATE INDEX IF NOT EXISTS idx_secure_documents_job ON secure_documents(job_id);
CREATE INDEX IF NOT EXISTS idx_secure_documents_owner ON secure_documents(owner_id);
CREATE INDEX IF NOT EXISTS idx_secure_documents_hash ON secure_documents(sha256_hash);
CREATE INDEX IF NOT EXISTS idx_secure_documents_classification ON secure_documents(classification);

-- 4. Tabela document_access_logs (auditoria imutável de acesso e download de documentos)
CREATE TABLE IF NOT EXISTS document_access_logs (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES secure_documents(id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(100),
    user_agent VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_document_access_logs_doc ON document_access_logs(document_id);
CREATE INDEX IF NOT EXISTS idx_document_access_logs_user ON document_access_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_document_access_logs_time ON document_access_logs(timestamp DESC);

-- 5. Adicionar colunas de integridade e assinatura na tabela contracts (aditivo e não destrutivo)
DO $$ BEGIN
    ALTER TABLE contracts ADD COLUMN IF NOT EXISTS conflict_status VARCHAR(50) NOT NULL DEFAULT 'CLEAR';
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE contracts ADD COLUMN IF NOT EXISTS terms_version VARCHAR(50) NOT NULL DEFAULT 'v1.0';
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE contracts ADD COLUMN IF NOT EXISTS signed_at TIMESTAMP;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE contracts ADD COLUMN IF NOT EXISTS hash_receipt VARCHAR(128);
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_contracts_conflict_status ON contracts(conflict_status);
CREATE INDEX IF NOT EXISTS idx_contracts_hash_receipt ON contracts(hash_receipt);
