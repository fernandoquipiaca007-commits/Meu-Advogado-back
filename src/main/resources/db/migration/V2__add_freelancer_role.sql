-- V2__add_freelancer_role.sql
-- Adiciona a role ROLE_FREELANCER que estava faltando na migração V1
INSERT INTO roles (name) VALUES ('ROLE_FREELANCER') ON CONFLICT (name) DO NOTHING;
