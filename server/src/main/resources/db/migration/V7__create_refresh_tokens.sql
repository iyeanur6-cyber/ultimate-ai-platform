-- ═══════════════════════════════════════════════════
-- V7: Create Refresh Tokens Table
-- JWT refresh tokens stored server-side.
-- Enables token revocation on logout.
-- ═══════════════════════════════════════════════════

CREATE TABLE refresh_tokens (
                                id              UUID            NOT NULL DEFAULT gen_random_uuid(),
                                user_id         UUID            NOT NULL,
                                token_hash      VARCHAR(255)    NOT NULL,
                                expires_at      TIMESTAMPTZ     NOT NULL,
                                created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                revoked         BOOLEAN         NOT NULL DEFAULT FALSE,
                                revoked_at      TIMESTAMPTZ,
                                device_info     VARCHAR(500),

                                CONSTRAINT pk_refresh_tokens
                                    PRIMARY KEY (id),
                                CONSTRAINT fk_refresh_tokens_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users (id)
                                        ON DELETE CASCADE,
                                CONSTRAINT uq_refresh_tokens_hash
                                    UNIQUE (token_hash),
                                CONSTRAINT chk_refresh_tokens_revoked
                                    CHECK (
                                        (revoked = FALSE AND revoked_at IS NULL)
                                            OR
                                        (revoked = TRUE AND revoked_at IS NOT NULL)
                                        )
);

-- Fast lookup during token refresh (every API call)
CREATE INDEX idx_refresh_tokens_hash
    ON refresh_tokens (token_hash)
    WHERE revoked = FALSE;

-- Find all active tokens for a user (logout all devices)
CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens (user_id, revoked)
    WHERE revoked = FALSE;

-- Cleanup job: find expired tokens
CREATE INDEX idx_refresh_tokens_expires
    ON refresh_tokens (expires_at)
    WHERE revoked = FALSE;

COMMENT ON TABLE refresh_tokens IS
    'JWT refresh tokens. Stored hashed for security.';
COMMENT ON COLUMN refresh_tokens.token_hash IS
    'SHA-256 hash of the actual refresh token. Never store plain token.';
COMMENT ON COLUMN refresh_tokens.device_info IS
    'e.g. "Jarvis CLI v0.1.0 / Windows 11" for session management UI.';