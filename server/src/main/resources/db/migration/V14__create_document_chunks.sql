-- ═══════════════════════════════════════════════════
-- V14: Create Document Chunks Table
-- Phase 3: RAG Engine
--
-- FIXES (CodeRabbit):
-- Issue #8: Composite FK (document_id, user_id)
--           ensures chunk ownership matches document
-- Issue #9: Added HNSW index for vector search
-- ═══════════════════════════════════════════════════

CREATE TABLE document_chunks (

                                 id              UUID            NOT NULL
                                                                          DEFAULT gen_random_uuid(),
                                 document_id     UUID            NOT NULL,
                                 user_id         UUID            NOT NULL,
                                 content         TEXT            NOT NULL,
                                 chunk_index     INTEGER         NOT NULL DEFAULT 0,
                                 page_number     INTEGER,
                                 token_count     INTEGER         NOT NULL DEFAULT 0,
                                 embedding       vector(768),
                                 created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- ── Constraints ──────────────────────────────

                                 CONSTRAINT pk_document_chunks
                                     PRIMARY KEY (id),

    -- FIX Issue #8: Composite FK enforces ownership.
    -- chunk.user_id MUST match documents.user_id.
    -- Without this: chunk could reference valid document_id
    -- but different user_id → cross-tenant data leak.
    -- Requires uq_documents_id_user index in V13.
                                 CONSTRAINT fk_chunks_document_user
                                     FOREIGN KEY (document_id, user_id)
                                         REFERENCES documents (id, user_id)
                                         ON DELETE CASCADE,

    -- Keep user FK for direct user cascade deletes
                                 CONSTRAINT fk_chunks_user
                                     FOREIGN KEY (user_id)
                                         REFERENCES users (id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT chk_chunks_content_not_empty
                                     CHECK (LENGTH(TRIM(content)) > 0),

                                 CONSTRAINT chk_chunks_index_positive
                                     CHECK (chunk_index >= 0),

                                 CONSTRAINT chk_chunks_token_count
                                     CHECK (token_count >= 0)
);

-- ── Indexes ───────────────────────────────────────

CREATE INDEX idx_chunks_document_id
    ON document_chunks (document_id, chunk_index ASC);

CREATE INDEX idx_chunks_user_id
    ON document_chunks (user_id);

-- Partial index for non-null embeddings only
CREATE INDEX idx_chunks_embedding_not_null
    ON document_chunks (user_id)
    WHERE embedding IS NOT NULL;

-- FIX Issue #9: HNSW vector index for fast
-- approximate nearest-neighbor cosine search.
-- Without this: full table scan on every RAG query.
-- With HNSW: O(log n) search, ~99% accuracy.
--
-- HNSW parameters:
-- m = 16             (connections per node, default)
-- ef_construction = 64 (build accuracy vs speed tradeoff)
--
-- CodeRabbit: embedding <=> distance ranking is hot path.
-- Becomes bottleneck without ANN index at scale.
CREATE INDEX idx_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE embedding IS NOT NULL;

-- ── Semantic Search Function ──────────────────────

CREATE OR REPLACE FUNCTION search_chunks_by_embedding(
    p_user_id       UUID,
    p_embedding     vector(768),
    p_limit         INTEGER DEFAULT 5,
    p_min_similarity FLOAT DEFAULT 0.5,
    p_document_id   UUID DEFAULT NULL
)
RETURNS TABLE (
    id              UUID,
    document_id     UUID,
    content         TEXT,
    chunk_index     INTEGER,
    page_number     INTEGER,
    token_count     INTEGER,
    similarity      FLOAT
)
LANGUAGE SQL
STABLE
AS $$
SELECT
    c.id,
    c.document_id,
    c.content,
    c.chunk_index,
    c.page_number,
    c.token_count,
    1 - (c.embedding <=> p_embedding) AS similarity
FROM document_chunks c
WHERE
    c.user_id = p_user_id
  AND c.embedding IS NOT NULL
  AND 1 - (c.embedding <=> p_embedding) >= p_min_similarity
  AND (p_document_id IS NULL
    OR c.document_id = p_document_id)
ORDER BY
    c.embedding <=> p_embedding ASC
LIMIT p_limit;
$$;

COMMENT ON FUNCTION search_chunks_by_embedding IS
    'Semantic search using cosine similarity + HNSW index.
     Optional p_document_id filters to single document.
     Returns chunks ordered by relevance.';

COMMENT ON TABLE document_chunks IS
    'Chunks from uploaded documents with pgvector embeddings.
     HNSW index enables fast approximate semantic search.
     Composite FK guarantees chunk ownership = document owner.';

COMMENT ON INDEX idx_chunks_embedding_hnsw IS
    'HNSW index for fast ANN cosine distance search.
     ~99% accuracy vs exact search, O(log n) complexity.
     Only indexes rows with non-null embeddings.';