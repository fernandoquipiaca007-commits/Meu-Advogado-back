-- ============================================================
-- V4: Convert custom enum columns to VARCHAR(50)
-- Allows standard JPA / Hibernate @Enumerated(EnumType.STRING)
-- to persist seamlessly across all PostgreSQL drivers
-- ============================================================

DO $$ BEGIN
    ALTER TABLE jobs ALTER COLUMN job_type TYPE VARCHAR(50) USING job_type::text;
EXCEPTION WHEN others THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE jobs ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
EXCEPTION WHEN others THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE jobs ALTER COLUMN urgency TYPE VARCHAR(50) USING urgency::text;
EXCEPTION WHEN others THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE jobs ALTER COLUMN confidentiality TYPE VARCHAR(50) USING confidentiality::text;
EXCEPTION WHEN others THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE proposals ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
EXCEPTION WHEN others THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE contracts ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
EXCEPTION WHEN others THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE contract_milestones ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
EXCEPTION WHEN others THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
EXCEPTION WHEN others THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(50) USING type::text;
EXCEPTION WHEN others THEN NULL;
END $$;
