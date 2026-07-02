-- V9__Create_law_firms.sql
-- Creates the law firms infrastructure for LegalWork.

CREATE TABLE law_firms
(
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    cnpj        VARCHAR(20) UNIQUE,
    description TEXT,
    phone       VARCHAR(20),
    website     VARCHAR(255),
    logo_url    VARCHAR(500),
    address     VARCHAR(255),
    city        VARCHAR(100),
    state       VARCHAR(50),
    country     VARCHAR(50)  NOT NULL DEFAULT 'BR',
    is_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lawyer_firms
(
    lawyer_id   INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    firm_id     INTEGER NOT NULL REFERENCES law_firms (id) ON DELETE CASCADE,
    is_partner  BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lawyer_id, firm_id)
);

CREATE INDEX idx_lawyer_firms_lawyer ON lawyer_firms (lawyer_id);
CREATE INDEX idx_lawyer_firms_firm ON lawyer_firms (firm_id);

CREATE TABLE specialties
(
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE lawyer_specialties
(
    lawyer_id     INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    specialty_id  INTEGER NOT NULL REFERENCES specialties (id) ON DELETE CASCADE,
    PRIMARY KEY (lawyer_id, specialty_id)
);

INSERT INTO specialties (name) VALUES
('Direito Civil'),
('Direito Penal'),
('Direito Trabalhista'),
('Direito Tributário'),
('Direito Empresarial'),
('Direito de Família'),
('Direito do Consumidor'),
('Direito Imobiliário'),
('Direito Digital'),
('Direito Previdenciário'),
('Direito Administrativo'),
('Direito Ambiental');
