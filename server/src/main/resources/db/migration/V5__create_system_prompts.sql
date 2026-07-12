-- ═══════════════════════════════════════════════════
-- V5: Create System Prompts Table
-- Reusable system prompts (Jarvis personalities).
-- Seeded in V8 with default Jarvis prompt.
-- ═══════════════════════════════════════════════════

CREATE TABLE system_prompts (
                                id              UUID            NOT NULL DEFAULT gen_random_uuid(),
                                name            VARCHAR(100)    NOT NULL,
                                display_name    VARCHAR(200)    NOT NULL,
                                content         TEXT            NOT NULL,
                                description     TEXT,
                                is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
                                is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
                                created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

                                CONSTRAINT pk_system_prompts
                                    PRIMARY KEY (id),
                                CONSTRAINT uq_system_prompts_name
                                    UNIQUE (name),
                                CONSTRAINT chk_system_prompts_content_not_empty
                                    CHECK (LENGTH(TRIM(content)) > 0)
);

CREATE INDEX idx_system_prompts_default
    ON system_prompts (is_default)
    WHERE is_default = TRUE;

CREATE INDEX idx_system_prompts_active
    ON system_prompts (is_active)
    WHERE is_active = TRUE;

CREATE TRIGGER trigger_system_prompts_updated_at
    BEFORE UPDATE ON system_prompts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE system_prompts IS
    'Reusable system prompts defining Jarvis personality modes.';
COMMENT ON COLUMN system_prompts.content IS
    'Supports template variables: {username}, {datetime}, {model}';