-- ═══════════════════════════════════════════════════
-- V4: Create Messages Table
-- Every message in every conversation.
-- The most important table in Phase 1.
-- ═══════════════════════════════════════════════════

CREATE TABLE messages (
                          id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
                          session_id          UUID        NOT NULL,
                          role                VARCHAR(20) NOT NULL,
                          content             TEXT        NOT NULL,
                          provider_id         UUID,
                          model_name          VARCHAR(100),
                          prompt_tokens       INTEGER,
                          completion_tokens   INTEGER,
                          total_tokens        INTEGER,
                          duration_ms         INTEGER,
                          finish_reason       VARCHAR(50),
                          is_error            BOOLEAN     NOT NULL DEFAULT FALSE,
                          error_message       TEXT,
                          metadata            JSONB,
                          created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                          CONSTRAINT pk_messages
                              PRIMARY KEY (id),
                          CONSTRAINT fk_messages_session
                              FOREIGN KEY (session_id)
                                  REFERENCES chat_sessions (id)
                                  ON DELETE CASCADE,
                          CONSTRAINT fk_messages_provider
                              FOREIGN KEY (provider_id)
                                  REFERENCES ai_providers (id)
                                  ON DELETE SET NULL,
                          CONSTRAINT chk_messages_role
                              CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
                          CONSTRAINT chk_messages_content_not_empty
                              CHECK (LENGTH(TRIM(content)) > 0),
                          CONSTRAINT chk_messages_finish_reason
                              CHECK (finish_reason IS NULL OR finish_reason IN (
                                                                                'STOP', 'LENGTH', 'TOOL_CALLS', 'ERROR', 'CANCELLED'
                                  ))
);

-- THE MOST CRITICAL INDEX in the entire database.
-- Every chat load runs: "give me all messages for
-- session X ordered by time"
-- This index makes it instant.
CREATE INDEX idx_messages_session_time
    ON messages (session_id, created_at ASC);

-- For loading recent messages efficiently
CREATE INDEX idx_messages_session_id
    ON messages (session_id);

-- For token usage analytics
CREATE INDEX idx_messages_role
    ON messages (session_id, role);

-- For error analysis
CREATE INDEX idx_messages_errors
    ON messages (session_id, is_error)
    WHERE is_error = TRUE;

COMMENT ON TABLE messages IS
    'All chat messages. Central table for conversation history.';
COMMENT ON COLUMN messages.model_name IS
    'Denormalized: exact model used. Preserved even if provider config changes.';
COMMENT ON COLUMN messages.metadata IS
    'Flexible JSONB for tool calls, citations, future features.';
COMMENT ON COLUMN messages.finish_reason IS
    'Why generation stopped: STOP=natural, LENGTH=truncated, ERROR=failed.';