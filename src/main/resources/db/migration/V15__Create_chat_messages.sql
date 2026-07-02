-- V15__Create_chat_messages.sql
-- Creates chat_messages table for contract-based messaging

CREATE TABLE chat_messages
(
    message_id  SERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts (contract_id) ON DELETE CASCADE,
    sender_id   INTEGER NOT NULL REFERENCES users (id),
    message     TEXT NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_contract ON chat_messages (contract_id);
CREATE INDEX idx_chat_contract_created ON chat_messages (contract_id, created_at ASC);
CREATE INDEX idx_chat_sender ON chat_messages (sender_id);
