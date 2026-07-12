package ai.ultimate.rag;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an uploaded document in the RAG system.
 *
 * Maps to the 'documents' table (V13 migration).
 * Stores metadata only — actual content in document_chunks.
 *
 * FIXES (CodeRabbit):
 * - withReady() now validates totalChunks >= 0
 *   Prevents invalid state from reaching persistence.
 */
@Table("documents")
public record Document(

        @Id
        UUID id,

        @Column("user_id")
        UUID userId,

        String filename,

        @Column("file_type")
        DocumentFileType fileType,

        @Column("file_size_bytes")
        long fileSizeBytes,

        DocumentStatus status,

        @Column("chunk_count")
        int chunkCount,

        String description,

        @Column("error_message")
        String errorMessage,

        @Column("created_at")
        Instant createdAt,

        @Column("updated_at")
        Instant updatedAt

) {
    public static Document create(
            UUID userId,
            String filename,
            DocumentFileType fileType,
            long fileSizeBytes,
            String description) {
        return new Document(
                UUID.randomUUID(),
                userId,
                filename,
                fileType,
                fileSizeBytes,
                DocumentStatus.PENDING,
                0,
                description,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    public Document withProcessing() {
        return new Document(
                id, userId, filename, fileType,
                fileSizeBytes,
                DocumentStatus.PROCESSING,
                chunkCount, description, null,
                createdAt, Instant.now()
        );
    }

    /**
     * FIX: Guard against negative chunk counts.
     * CodeRabbit Issue #4:
     * Negative totalChunks is invalid domain state.
     * Reject at domain boundary before reaching DB.
     *
     * @param totalChunks must be >= 0
     * @throws IllegalArgumentException if negative
     */
    public Document withReady(int totalChunks) {
        if (totalChunks < 0) {
            throw new IllegalArgumentException(
                    "totalChunks must be >= 0, "
                            + "got: " + totalChunks);
        }
        return new Document(
                id, userId, filename, fileType,
                fileSizeBytes,
                DocumentStatus.READY,
                totalChunks, description, null,
                createdAt, Instant.now()
        );
    }

    public Document withFailed(String error) {
        return new Document(
                id, userId, filename, fileType,
                fileSizeBytes,
                DocumentStatus.FAILED,
                chunkCount, description, error,
                createdAt, Instant.now()
        );
    }
}