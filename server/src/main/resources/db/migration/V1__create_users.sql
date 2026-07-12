-- ═══════════════════════════════════════════════════
-- V1: Create Users Table
-- Users of this Ultimate instance.
-- First user created = admin.
-- ═══════════════════════════════════════════════════

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- pgcrypto provides gen_random_uuid() for UUID v4
-- We use application-generated UUID v7 for IDs
-- But pgcrypto is useful for future cryptographic needs

CREATE TABLE users (
                       id              UUID            NOT NULL DEFAULT gen_random_uuid(),
                       username        VARCHAR(50)     NOT NULL,
                       email           VARCHAR(255)    NOT NULL,
                       password_hash   VARCHAR(255)    NOT NULL,
                       display_name    VARCHAR(100),
                       role            VARCHAR(20)     NOT NULL DEFAULT 'USER',
                       is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
                       created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                       last_login_at   TIMESTAMPTZ,

                       CONSTRAINT pk_users
                           PRIMARY KEY (id),
                       CONSTRAINT uq_users_username
                           UNIQUE (username),
                       CONSTRAINT uq_users_email
                           UNIQUE (email),
                       CONSTRAINT chk_users_role
                           CHECK (role IN ('ADMIN', 'USER')),
                       CONSTRAINT chk_users_username_length
                           CHECK (LENGTH(username) >= 3)
);

-- Index for login lookup (most common query)
CREATE INDEX idx_users_username
    ON users (username);

CREATE INDEX idx_users_email
    ON users (email);

-- Index for admin queries
CREATE INDEX idx_users_role_active
    ON users (role, is_active);

-- Automatic updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE users IS
    'Ultimate platform users. First user created becomes admin.';
COMMENT ON COLUMN users.password_hash IS
    'Argon2id hashed password. Never store plain text.';
COMMENT ON COLUMN users.role IS
    'ADMIN: full access. USER: own data only.';