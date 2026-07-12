-- ═══════════════════════════════════════════════════
-- V13: Create Documents Table
-- Phase 3: RAG Engine
--
-- FIXES (CodeRabbit):
-- Issue #7: Added CHECK (chunk_count >= 0)
-- Issue #8: Added UNIQUE (id, user_id) for composite
--           FK in document_chunks table
-- ═══════════════════════════════════════════════════

CREATE TABLE documents (

                           id              UUID            NOT NULL
                                                                    DEFAULT gen_random_uuid(),
                           user_id         UUID            NOT NULL,
                           filename        VARCHAR(500)    NOT NULL,
                           file_type       VARCHAR(20)     NOT NULL,
                           file_size_bytes BIGINT          NOT NULL DEFAULT 0,
                           status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
                           chunk_count     INTEGER         NOT NULL DEFAULT 0,
                           description     TEXT,
                           error_message   TEXT,
                           created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                           updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- ── Constraints ──────────────────────────────

                           CONSTRAINT pk_documents
                               PRIMARY KEY (id),

                           CONSTRAINT fk_documents_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users (id)
                                   ON DELETE CASCADE,

                           CONSTRAINT chk_documents_file_type
                               CHECK (file_type IN ('PDF', 'TXT', 'MARKDOWN')),

                           CONSTRAINT chk_documents_status
                               CHECK (status IN (
                                                 'PENDING', 'PROCESSING', 'READY', 'FAILED'
                                   )),

                           CONSTRAINT chk_documents_filename_not_empty
                               CHECK (LENGTH(TRIM(filename)) > 0),

                           CONSTRAINT chk_documents_file_size
                               CHECK (file_size_bytes >= 0),

    -- FIX Issue #7: Prevent negative chunk counts
    -- CodeRabbit: chunk_count is lifecycle state,
    -- negative value breaks UI counts + metrics
                           CONSTRAINT chk_documents_chunk_count
                               CHECK (chunk_count >= 0)
);

-- ── Indexes ───────────────────────────────────────

CREATE INDEX idx_documents_user_id
    ON documents (user_id);

CREATE INDEX idx_documents_user_status
    ON documents (user_id, status);

-- FIX Issue #8: Composite unique constraint for
-- document_id + user_id ownership verification.
-- This enables document_chunks to reference BOTH
-- columns ensuring chunk.user_id always matches
-- the owning document's user_id.
-- CodeRabbit: prevents cross-tenant integrity risk.
CREATE UNIQUE INDEX uq_documents_id_user
    ON documents (id, user_id);

-- ── Auto-update trigger ───────────────────────────

CREATE TRIGGER trigger_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ── Comments ──────────────────────────────────────

COMMENT ON TABLE documents IS
    'Documents uploaded by users for RAG search.
     Actual content stored in document_chunks table.';

COMMENT ON COLUMN documents.chunk_count IS
    'Number of chunks created from this document.
     Always >= 0. Updated when processing completes.';

COMMENT ON INDEX uq_documents_id_user IS
    'Enables composite FK in document_chunks.
     Guarantees chunk.user_id matches document owner.';