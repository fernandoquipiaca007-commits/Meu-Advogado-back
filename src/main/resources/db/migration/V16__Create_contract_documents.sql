-- V16__Create_contract_documents.sql
-- Creates contract_documents table for file attachments on contracts

CREATE TABLE contract_documents
(
    document_id   SERIAL PRIMARY KEY,
    contract_id   INTEGER NOT NULL REFERENCES contracts (contract_id) ON DELETE CASCADE,
    uploaded_by   INTEGER NOT NULL REFERENCES users (id),
    file_name     VARCHAR(255) NOT NULL,
    file_size     BIGINT NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    storage_path  VARCHAR(500) NOT NULL,
    description   TEXT,
    category      VARCHAR(50) DEFAULT 'other',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_contract ON contract_documents (contract_id);
CREATE INDEX idx_documents_uploader ON contract_documents (uploaded_by);
