-- ═══════════════════════════════════════════════════
-- V3: Create Chat Sessions Table
-- A session = one conversation thread.
-- Like a chat "window" in ChatGPT.
-- ═══════════════════════════════════════════════════

CREATE TABLE chat_sessions (
                               id              UUID            NOT NULL DEFAULT gen_random_uuid(),
                               user_id         UUID            NOT NULL,
                               title           VARCHAR(500),
                               status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
                               provider_id     UUID,
                               system_prompt   TEXT,
                               message_count   INTEGER         NOT NULL DEFAULT 0,
                               total_tokens    INTEGER         NOT NULL DEFAULT 0,
                               created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                               updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                               last_message_at TIMESTAMPTZ,

                               CONSTRAINT pk_chat_sessions
                                   PRIMARY KEY (id),
                               CONSTRAINT fk_chat_sessions_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users (id)
                                       ON DELETE CASCADE,
                               CONSTRAINT fk_chat_sessions_provider
                                   FOREIGN KEY (provider_id)
                                       REFERENCES ai_providers (id)
                                       ON DELETE SET NULL,
                               CONSTRAINT chk_chat_sessions_status
                                   CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
                               CONSTRAINT chk_chat_sessions_message_count
                                   CHECK (message_count >= 0)
);

-- Most critical index: user's recent sessions
CREATE INDEX idx_chat_sessions_user_recent
    ON chat_sessions (user_id, last_message_at DESC NULLS LAST)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_chat_sessions_user_id
    ON chat_sessions (user_id);

CREATE INDEX idx_chat_sessions_status
    ON chat_sessions (status);

CREATE TRIGGER trigger_chat_sessions_updated_at
    BEFORE UPDATE ON chat_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE chat_sessions IS
    'Chat conversation threads. Each session has isolated memory.';
COMMENT ON COLUMN chat_sessions.system_prompt IS
    'Custom system prompt for this session. NULL = use default.';
COMMENT ON COLUMN chat_sessions.message_count IS
    'Denormalized count for fast display. Updated by application.';