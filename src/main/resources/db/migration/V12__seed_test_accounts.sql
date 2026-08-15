-- ============================================================
-- V12: Seed Test Accounts (Advogado e Cliente Verificados)
-- Senha padrão: LWork2026!
-- ============================================================

-- 1. Ensure Roles Exist
INSERT INTO roles (name, created_at)
VALUES 
    ('ROLE_LAWYER', NOW()),
    ('ROLE_CLIENT', NOW()),
    ('ROLE_FREELANCER', NOW()),
    ('ROLE_ADMIN', NOW()),
    ('ROLE_FIRM', NOW())
ON CONFLICT (name) DO NOTHING;

-- 2. Insert Test Lawyer: advogado.teste@legawork.com / LWork2026!
-- BCrypt: $2a$10$e8wA/9K83b7zP7L9w115ee8o429z6KqY.zJslYmbk3wPmsuM0aO2K (LWork2026!)
INSERT INTO users (first_name, last_name, email, password, account_locked, account_enabled, created_at)
VALUES (
    'Rodrigo',
    'Silveira',
    'advogado.teste@legawork.com',
    '$2a$10$wI5f2hMfqIqj1kUsmE1O0O.YQZ6xPz5d4aHkJk1y3Tqf3PmsuM0aO',
    FALSE,
    TRUE,
    NOW()
)
ON CONFLICT (email) DO UPDATE 
SET 
    account_locked = FALSE,
    account_enabled = TRUE,
    password = '$2a$10$wI5f2hMfqIqj1kUsmE1O0O.YQZ6xPz5d4aHkJk1y3Tqf3PmsuM0aO';

-- Assign Roles for Test Lawyer
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'advogado.teste@legawork.com' AND r.name IN ('ROLE_LAWYER', 'ROLE_FREELANCER')
ON CONFLICT DO NOTHING;

-- Insert/Update User Profile for Test Lawyer
INSERT INTO user_profiles (
    user_id, title, description, hourly_rate, location, oab_number, oab_state, country, phone, verification_status, experience_years
)
SELECT 
    u.id,
    'Especialista em Direito Empresarial & Contratos',
    'Advogado sênior com mais de 12 anos de experiência em estruturação societária, M&A, contratos comerciais e compliance LGPD.',
    280.00,
    'São Paulo, SP',
    '412.980',
    'SP',
    'BR',
    '(11) 98765-4321',
    'VERIFIED',
    12
FROM users u WHERE u.email = 'advogado.teste@legawork.com'
ON CONFLICT (user_id) DO UPDATE
SET 
    verification_status = 'VERIFIED',
    oab_number = '412.980',
    oab_state = 'SP',
    title = 'Especialista em Direito Empresarial & Contratos',
    location = 'São Paulo, SP',
    country = 'BR';


-- 3. Insert Test Client: cliente.teste@legawork.com / LWork2026!
INSERT INTO users (first_name, last_name, email, password, account_locked, account_enabled, created_at)
VALUES (
    'Mariana',
    'Oliveira',
    'cliente.teste@legawork.com',
    '$2a$10$wI5f2hMfqIqj1kUsmE1O0O.YQZ6xPz5d4aHkJk1y3Tqf3PmsuM0aO',
    FALSE,
    TRUE,
    NOW()
)
ON CONFLICT (email) DO UPDATE 
SET 
    account_locked = FALSE,
    account_enabled = TRUE,
    password = '$2a$10$wI5f2hMfqIqj1kUsmE1O0O.YQZ6xPz5d4aHkJk1y3Tqf3PmsuM0aO';

-- Assign Roles for Test Client
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'cliente.teste@legawork.com' AND r.name = 'ROLE_CLIENT'
ON CONFLICT DO NOTHING;

-- Insert/Update User Profile for Test Client
INSERT INTO user_profiles (
    user_id, title, description, company_name, client_type, location, country, phone, verification_status
)
SELECT 
    u.id,
    'Diretoria Jurídica',
    'Representante legal da Oliveira Tech Solutions Ltda.',
    'Oliveira Tech Solutions Ltda',
    'EMPRESARIAL',
    'São Paulo, SP',
    'BR',
    '(11) 91234-5678',
    'VERIFIED'
FROM users u WHERE u.email = 'cliente.teste@legawork.com'
ON CONFLICT (user_id) DO UPDATE
SET 
    verification_status = 'VERIFIED',
    company_name = 'Oliveira Tech Solutions Ltda',
    client_type = 'EMPRESARIAL',
    location = 'São Paulo, SP',
    country = 'BR';
