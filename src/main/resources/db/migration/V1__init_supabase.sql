-- ============================================================
-- MIGRAÇÃO COMPLETA PARA SUPABASE
-- Versão: V1
-- Descrição: Cria toda a estrutura do banco de dados
-- ============================================================

-- ============================================================
-- 1. ENUMS
-- ============================================================
DO $$ BEGIN
    CREATE TYPE contract_status AS ENUM ('Active', 'Completed', 'Terminated', 'Cancelled');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE job_status AS ENUM ('Open', 'InProposal', 'InProgress', 'Completed', 'Archived');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE job_type AS ENUM ('Hourly', 'Fixed', 'ProBono', 'Contingency');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE milestone_status AS ENUM ('Pending', 'InProgress', 'Completed', 'Cancelled');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE payment_status AS ENUM ('Pending', 'Completed', 'Failed', 'Refunded');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE proposal_status AS ENUM ('Pending', 'Accepted', 'Rejected', 'Countered', 'Withdrawn');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE notification_type AS ENUM (
        'PROPOSAL_RECEIVED', 'PROPOSAL_ACCEPTED', 'PROPOSAL_REJECTED',
        'CONTRACT_CREATED', 'CONTRACT_COMPLETED', 'CONTRACT_TERMINATED',
        'MILESTONE_COMPLETED', 'PAYMENT_RECEIVED', 'PAYMENT_CREATED',
        'REVIEW_RECEIVED', 'NEW_MESSAGE'
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE urgency_level AS ENUM ('Low', 'Medium', 'High', 'Urgent');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE confidentiality_level AS ENUM ('Public', 'Private', 'Confidential');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ============================================================
-- 2. TABELAS PRINCIPAIS
-- ============================================================

-- 2.1. Users (autenticação principal)
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    account_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    verification_token VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_modified_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- 2.2. Roles (papéis do sistema)
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_modified_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);

-- 2.3. User-Roles (M:N)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 2.4. Skills (habilidades)
CREATE TABLE IF NOT EXISTS skills (
    skill_id SERIAL PRIMARY KEY,
    skill_name VARCHAR(50) NOT NULL UNIQUE
);

-- 2.5. Specialties (especialidades jurídicas)
CREATE TABLE IF NOT EXISTS specialties (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- 2.6. User Profiles (perfil estendido do usuário)
CREATE TABLE IF NOT EXISTS user_profiles (
    profile_id SERIAL PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    hourly_rate DECIMAL(19, 2),
    location VARCHAR(255),
    oab_number VARCHAR(255),
    oab_state VARCHAR(255),
    country VARCHAR(255),
    phone VARCHAR(255),
    photo_url VARCHAR(255),
    date_of_birth DATE,
    languages TEXT,
    experience_years INTEGER,
    verification_status VARCHAR(255) NOT NULL DEFAULT 'unverified',
    client_type VARCHAR(255),
    company_name VARCHAR(255),
    user_id INTEGER UNIQUE REFERENCES users(id) ON DELETE CASCADE
);

-- 2.7. User-Skills (M:N)
CREATE TABLE IF NOT EXISTS user_skills (
    user_id INTEGER NOT NULL REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    skill_id INTEGER NOT NULL REFERENCES skills(skill_id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, skill_id)
);

-- 2.8. Lawyer-Specialties (M:N)
CREATE TABLE IF NOT EXISTS lawyer_specialties (
    lawyer_id INTEGER NOT NULL REFERENCES user_profiles(user_id) ON DELETE CASCADE,
    specialty_id INTEGER NOT NULL REFERENCES specialties(id) ON DELETE CASCADE,
    PRIMARY KEY (lawyer_id, specialty_id)
);

-- 2.9. Jobs (casos jurídicos)
CREATE TABLE IF NOT EXISTS jobs (
    job_id SERIAL PRIMARY KEY,
    client_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    budget DECIMAL NOT NULL,
    job_type job_type NOT NULL,
    status job_status NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    urgency urgency_level NOT NULL DEFAULT 'Medium',
    confidentiality confidentiality_level NOT NULL DEFAULT 'Public',
    estimated_value DECIMAL(12, 2),
    deadline DATE,
    specialty_id INTEGER REFERENCES specialties(id) ON DELETE SET NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP,
    closed_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_jobs_urgency ON jobs(urgency);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_specialty ON jobs(specialty_id);
CREATE INDEX IF NOT EXISTS idx_jobs_archived ON jobs(archived);

-- 2.10. Job-Skills (M:N)
CREATE TABLE IF NOT EXISTS job_skills (
    job_id INTEGER NOT NULL REFERENCES jobs(job_id) ON DELETE CASCADE,
    skill_id INTEGER NOT NULL REFERENCES skills(skill_id) ON DELETE CASCADE,
    PRIMARY KEY (job_id, skill_id)
);

-- 2.11. Proposals (propostas de advogados)
CREATE TABLE IF NOT EXISTS proposals (
    proposal_id SERIAL PRIMARY KEY,
    job_id INTEGER NOT NULL REFERENCES jobs(job_id) ON DELETE CASCADE,
    freelancer_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cover_letter TEXT,
    proposed_rate DECIMAL(10, 2),
    status proposal_status NOT NULL DEFAULT 'Pending',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    proposed_duration INTEGER,
    strategy TEXT,
    total_value DECIMAL(12, 2),
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_proposals_job ON proposals(job_id);
CREATE INDEX IF NOT EXISTS idx_proposals_lawyer ON proposals(freelancer_id);
CREATE INDEX IF NOT EXISTS idx_proposals_status ON proposals(status);

-- 2.12. Contracts (contratos/mandatos)
CREATE TABLE IF NOT EXISTS contracts (
    contract_id SERIAL PRIMARY KEY,
    job_id INTEGER NOT NULL REFERENCES jobs(job_id) ON DELETE CASCADE,
    client_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lawyer_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proposal_id INTEGER UNIQUE REFERENCES proposals(proposal_id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    total_value DECIMAL(12, 2),
    start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    end_date DATE,
    status contract_status NOT NULL DEFAULT 'Active',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_contracts_job ON contracts(job_id);
CREATE INDEX IF NOT EXISTS idx_contracts_client ON contracts(client_id);
CREATE INDEX IF NOT EXISTS idx_contracts_lawyer ON contracts(lawyer_id);
CREATE INDEX IF NOT EXISTS idx_contracts_status ON contracts(status);

-- 2.13. Contract Milestones (etapas do contrato)
CREATE TABLE IF NOT EXISTS contract_milestones (
    milestone_id SERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts(contract_id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    amount DECIMAL(12, 2),
    due_date DATE,
    status milestone_status NOT NULL DEFAULT 'Pending',
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_milestones_contract ON contract_milestones(contract_id);

-- 2.14. Contract Documents (documentos do contrato)
CREATE TABLE IF NOT EXISTS contract_documents (
    document_id SERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts(contract_id) ON DELETE CASCADE,
    uploaded_by INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL DEFAULT 'other',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_documents_contract ON contract_documents(contract_id);
CREATE INDEX IF NOT EXISTS idx_documents_uploader ON contract_documents(uploaded_by);

-- 2.15. Payments (pagamentos)
CREATE TABLE IF NOT EXISTS payments (
    payment_id SERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts(contract_id) ON DELETE CASCADE,
    milestone_id INTEGER REFERENCES contract_milestones(milestone_id) ON DELETE SET NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status payment_status NOT NULL DEFAULT 'Pending',
    payment_date TIMESTAMP NOT NULL DEFAULT NOW(),
    description VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_payments_contract ON payments(contract_id);
CREATE INDEX IF NOT EXISTS idx_payments_milestone ON payments(milestone_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

-- 2.16. Reviews (avaliações)
CREATE TABLE IF NOT EXISTS reviews (
    review_id SERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts(contract_id) ON DELETE CASCADE,
    reviewer_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reviewee_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_reviews_contract ON reviews(contract_id);
CREATE INDEX IF NOT EXISTS idx_reviews_reviewer ON reviews(reviewer_id);
CREATE INDEX IF NOT EXISTS idx_reviews_reviewee ON reviews(reviewee_id);

-- 2.17. Chat Messages (mensagens do chat)
CREATE TABLE IF NOT EXISTS chat_messages (
    message_id SERIAL PRIMARY KEY,
    contract_id INTEGER NOT NULL REFERENCES contracts(contract_id) ON DELETE CASCADE,
    sender_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_contract ON chat_messages(contract_id);
CREATE INDEX IF NOT EXISTS idx_chat_contract_created ON chat_messages(contract_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_chat_sender ON chat_messages(sender_id);

-- 2.18. Notifications (notificações)
CREATE TABLE IF NOT EXISTS notifications (
    notification_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type notification_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    reference_type VARCHAR(50),
    reference_id INTEGER,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_created ON notifications(user_id, created_at DESC);

-- 2.19. Audit Logs (logs de auditoria)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    action VARCHAR(50) NOT NULL,
    details VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_logs(created_at);

-- 2.20. Refresh Tokens (tokens de atualização JWT)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id SERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    user_id INTEGER UNIQUE REFERENCES users(id) ON DELETE CASCADE
);

-- 2.21. Law Firms (escritórios de advocacia)
CREATE TABLE IF NOT EXISTS law_firms (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    cnpj VARCHAR(255) UNIQUE,
    description TEXT,
    phone VARCHAR(255),
    website VARCHAR(255),
    logo_url VARCHAR(255),
    address VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    country VARCHAR(255) NOT NULL DEFAULT 'BR',
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 3. SEEDS (dados iniciais)
-- ============================================================

-- 3.1. Roles padrão
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_CLIENT'),
    ('ROLE_LAWYER'),
    ('ROLE_FIRM')
ON CONFLICT (name) DO NOTHING;

-- 3.2. Admin padrão
-- NOTA: A senha abaixo é um bcrypt hash de 'admin123'
-- O backend cria o admin automaticamente via CommandLineRunner
-- Se precisar de criar manualmente, use:
-- INSERT INTO users (first_name, last_name, email, password, account_enabled)
-- VALUES ('Admin', 'Sistema', 'admin@meuadvogado.com',
--         '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true)
-- ON CONFLICT (email) DO NOTHING;
-- E depois associe o role:
-- INSERT INTO user_roles (user_id, role_id)
-- VALUES ((SELECT id FROM users WHERE email = 'admin@meuadvogado.com'),
--         (SELECT id FROM roles WHERE name = 'ROLE_ADMIN'));

-- ============================================================
-- 4. RLS (Row Level Security) - Supabase Security
-- ============================================================

-- Habilitar RLS em todas as tabelas
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE specialties ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE lawyer_specialties ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE contracts ENABLE ROW LEVEL SECURITY;
ALTER TABLE contract_milestones ENABLE ROW LEVEL SECURITY;
ALTER TABLE contract_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE law_firms ENABLE ROW LEVEL SECURITY;

-- NOTA: O backend Spring Boot gerencia a autenticação via JWT,
-- não via Supabase Auth. O backend usa a service_role_key que bypassa RLS.
-- Como o auth.uid() do Supabase retorna UUID e os IDs do JPA são INTEGER,
-- as políticas RLS baseadas em auth.uid() NÃO funcionam com este schema.
-- Para segurança a nível de aplicação, confiamos no Spring Security + JWT.
--
-- Se no futuro quiseres usar Supabase Auth diretamente (ex: dashboard),
-- as tabelas precisarão de uma coluna auth_user_id UUID adicional.

-- Política: acesso público a skills e specialties (leitura)
CREATE POLICY "Skills are publicly readable"
    ON skills FOR SELECT USING (true);

CREATE POLICY "Specialties are publicly readable"
    ON specialties FOR SELECT USING (true);

-- Política: acesso público a jobs abertos (leitura)
CREATE POLICY "Open jobs are publicly readable"
    ON jobs FOR SELECT USING (status = 'Open' OR auth.role() = 'service_role');

-- ============================================================
-- 5. TRIGGERS (atualização automática de timestamps)
-- ============================================================

-- Trigger function para updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Aplicar trigger onde aplicável
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_proposals_updated_at') THEN
        CREATE TRIGGER update_proposals_updated_at
            BEFORE UPDATE ON proposals
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_contracts_updated_at') THEN
        CREATE TRIGGER update_contracts_updated_at
            BEFORE UPDATE ON contracts
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- ============================================================
-- 6. FUNÇÕES ÚTEIS
-- ============================================================

-- Função para contar propostas não lidas de um job
CREATE OR REPLACE FUNCTION count_job_proposals(p_job_id INTEGER)
RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM proposals
    WHERE job_id = p_job_id;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Função para contar notificações não lidas de um usuário
CREATE OR REPLACE FUNCTION count_unread_notifications(p_user_id INTEGER)
RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM notifications
    WHERE user_id = p_user_id AND is_read = FALSE;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- FIM DA MIGRAÇÃO V1
-- ============================================================
