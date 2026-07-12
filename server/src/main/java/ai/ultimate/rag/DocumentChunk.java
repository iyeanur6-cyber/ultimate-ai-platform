package ai.ultimate.rag;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One chunk of text from an uploaded document.
 *
 * Maps to 'document_chunks' table (V13 migration).
 *
 * CHUNKING:
 * Documents are split into overlapping chunks of
 * ~500 tokens with 50 token overlap.
 * This preserves context across chunk boundaries.
 *
 * EMBEDDING:
 * Each chunk gets a 768-dim pgvector embedding
 * from Ollama nomic-embed-text.
 * Used for semantic similarity search.
 */
@Table("document_chunks")
public record DocumentChunk(

        @Id
        UUID id,

        @Column("document_id")
        UUID documentId,

        @Column("user_id")
        UUID userId,

        // The actual text — injected into AI prompts
        String content,

        @Column("chunk_index")
        int chunkIndex,

        @Column("page_number")
        Integer pageNumber,

        @Column("token_count")
        int tokenCount,

        @Column("created_at")
        Instant createdAt

        // NOTE: embedding (vector(768)) column exists in DB
        // but NOT mapped here — R2DBC cannot handle vector type
        // Embeddings stored/read via JdbcTemplate only
        // (MemoryEmbeddingRepository pattern)

) {
    /**
     * Create a new document chunk.
     * Embedding stored separately via JDBC after creation.
     *
     * @param documentId  which document this belongs to
     * @param userId      owner (for access control)
     * @param content     the chunk text
     * @param chunkIndex  position in document (0-based)
     * @param pageNumber  approximate page (null if unknown)
     * @param tokenCount  estimated tokens in this chunk
     */
    public static DocumentChunk create(
            UUID documentId,
            UUID userId,
            String content,
            int chunkIndex,
            Integer pageNumber,
            int tokenCount) {
        return new DocumentChunk(
                UUID.randomUUID(),
                documentId,
                userId,
                content,
                chunkIndex,
                pageNumber,
                tokenCount,
                Instant.now()
        );
    }
}