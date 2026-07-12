package ai.ultimate.rag;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for document_chunks table.
 *
 * FIXES (CodeRabbit):
 * Issue #5: All read/delete methods are now user-scoped
 * (documentId + userId) to prevent cross-tenant access.
 * In multi-user system, user X must never access
 * chunks belonging to user Y's documents.
 *
 * Original non-scoped methods kept for internal use
 * (DocumentEmbeddingService loads its own chunks).
 */
@Repository
public interface DocumentChunkRepository
        extends R2dbcRepository<DocumentChunk, UUID> {

    // ── User-scoped (USE THESE in controllers/services) ──

    /**
     * FIX Issue #5: All chunks for a document,
     * scoped to owner. Prevents cross-tenant access.
     * Use this in DocumentProcessingService.
     */
    Flux<DocumentChunk> findByDocumentIdAndUserIdOrderByChunkIndexAsc(
            UUID documentId, UUID userId);

    /**
     * FIX Issue #5: Count chunks scoped to owner.
     * Use this for public-facing count queries.
     */
    Mono<Long> countByDocumentIdAndUserId(
            UUID documentId, UUID userId);

    /**
     * FIX Issue #5: Delete chunks scoped to owner.
     * Use this when user deletes their document.
     */
    Mono<Void> deleteByDocumentIdAndUserId(
            UUID documentId, UUID userId);

    // ── Internal use only (no user scope) ────────────────

    /**
     * Internal: load all chunks for embedding pipeline.
     * DocumentEmbeddingService already verified ownership
     * via DocumentRepository.findByIdAndUserId().
     * Safe to use internally after ownership verified.
     */
    Flux<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(
            UUID documentId);

    /**
     * Internal: delete all chunks when doc deleted.
     * Called from DocumentService after verifying
     * document ownership via findByIdAndUserId().
     */
    Mono<Void> deleteByDocumentId(UUID documentId);

    /**
     * Internal: count chunks after processing.
     * Used to verify chunk count matches expectations.
     */
    Mono<Long> countByDocumentId(UUID documentId);

    /**
     * All chunks for a user across all documents.
     * Used for cross-document semantic search prep.
     */
    Flux<DocumentChunk> findByUserId(UUID userId);
}