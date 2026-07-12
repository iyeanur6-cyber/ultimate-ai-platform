package ai.ultimate.rag;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for the documents table.
 *
 * FIX (CodeRabbit Issue #6):
 * updateStatus() now takes DocumentStatus enum
 * instead of raw String. This prevents invalid
 * status values from reaching the database.
 * The R2DBC converter in R2dbcConfig handles
 * DocumentStatus → String conversion automatically.
 */
@Repository
public interface DocumentRepository
        extends R2dbcRepository<Document, UUID> {

    Flux<Document> findByUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId, DocumentStatus status);

    Flux<Document> findByUserIdOrderByCreatedAtDesc(
            UUID userId);

    Mono<Document> findByIdAndUserId(
            UUID id, UUID userId);

    Mono<Long> countByUserId(UUID userId);

    /**
     * FIX Issue #6: Status parameter is now
     * DocumentStatus enum (not raw String).
     * Prevents invalid status values at compile time.
     * R2dbcConfig converter handles enum → String.
     *
     * @param documentId  document to update
     * @param status      DocumentStatus enum value
     * @param chunkCount  chunk count (>= 0)
     * @param errorMessage null unless FAILED
     */
    @Modifying
    @Query("""
            UPDATE documents
            SET status = :status,
                chunk_count = :chunkCount,
                error_message = :errorMessage,
                updated_at = NOW()
            WHERE id = :documentId
            """)
    Mono<Integer> updateStatus(
            UUID documentId,
            DocumentStatus status,
            int chunkCount,
            String errorMessage);
}