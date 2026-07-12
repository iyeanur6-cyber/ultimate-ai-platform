-- ═══════════════════════════════════════════════════
-- V6: Create Conversation Summaries Table
-- When sessions get long, old messages are compressed
-- into summaries. Prevents context window overflow.
-- ═══════════════════════════════════════════════════

CREATE TABLE conversation_summaries (
                                        id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
                                        session_id              UUID        NOT NULL,
                                        summary_text            TEXT        NOT NULL,
                                        key_topics              JSONB,
                                        messages_covered        INTEGER     NOT NULL DEFAULT 0,
                                        message_range_start_id  UUID,
                                        message_range_end_id    UUID,
                                        token_count_before      INTEGER,
                                        token_count_after       INTEGER,
                                        compression_ratio       DECIMAL(5,4),
                                        created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                        CONSTRAINT pk_conversation_summaries
                                            PRIMARY KEY (id),
                                        CONSTRAINT fk_summaries_session
                                            FOREIGN KEY (session_id)
                                                REFERENCES chat_sessions (id)
                                                ON DELETE CASCADE,
                                        CONSTRAINT chk_summaries_messages_covered
                                            CHECK (messages_covered > 0),
                                        CONSTRAINT chk_summaries_compression_ratio
                                            CHECK (compression_ratio IS NULL
                                                OR (compression_ratio > 0 AND compression_ratio <= 1))
);

-- Get most recent summary for a session (common query)
CREATE INDEX idx_summaries_session_recent
    ON conversation_summaries (session_id, created_at DESC);

COMMENT ON TABLE conversation_summaries IS
    'Compressed conversation history. Prevents context window overflow.';
COMMENT ON COLUMN conversation_summaries.key_topics IS
    'JSON array of main topics discussed. e.g. ["WebFlux","Security","JWT"]';
COMMENT ON COLUMN conversation_summaries.compression_ratio IS
    'token_count_after / token_count_before. Lower = better compression.';