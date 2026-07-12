-- ═══════════════════════════════════════════════════
-- V2: Create AI Providers Table
-- Configures which AI models Ultimate can use.
-- Seeded in V8 with Ollama + Gemini defaults.
-- ═══════════════════════════════════════════════════

CREATE TABLE ai_providers (
                              id              UUID            NOT NULL DEFAULT gen_random_uuid(),
                              name            VARCHAR(100)    NOT NULL,
                              display_name    VARCHAR(200)    NOT NULL,
                              provider_type   VARCHAR(50)     NOT NULL,
                              model_name      VARCHAR(100)    NOT NULL,
                              base_url        VARCHAR(500),
                              is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
                              is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
                              temperature     DECIMAL(4,2)    NOT NULL DEFAULT 0.70,
                              max_tokens      INTEGER         NOT NULL DEFAULT 2048,
                              context_window  INTEGER         NOT NULL DEFAULT 128000,
                              created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                              updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

                              CONSTRAINT pk_ai_providers
                                  PRIMARY KEY (id),
                              CONSTRAINT uq_ai_providers_name
                                  UNIQUE (name),
                              CONSTRAINT chk_ai_providers_type
                                  CHECK (provider_type IN (
                                                           'OLLAMA', 'GEMINI', 'OPENROUTER',
                                                           'GROQ', 'HUGGINGFACE', 'CUSTOM'
                                      )),
                              CONSTRAINT chk_ai_providers_temperature
                                  CHECK (temperature >= 0.00 AND temperature <= 2.00),
                              CONSTRAINT chk_ai_providers_max_tokens
                                  CHECK (max_tokens > 0 AND max_tokens <= 128000)
);

CREATE INDEX idx_ai_providers_default
    ON ai_providers (is_default)
    WHERE is_default = TRUE;

CREATE INDEX idx_ai_providers_active
    ON ai_providers (is_active)
    WHERE is_active = TRUE;

CREATE TRIGGER trigger_ai_providers_updated_at
    BEFORE UPDATE ON ai_providers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE ai_providers IS
    'AI model provider configurations. Supports multiple providers.';
COMMENT ON COLUMN ai_providers.base_url IS
    'Only needed for self-hosted models like Ollama.';
COMMENT ON COLUMN ai_providers.is_default IS
    'Only one provider should have is_default=TRUE at a time.';