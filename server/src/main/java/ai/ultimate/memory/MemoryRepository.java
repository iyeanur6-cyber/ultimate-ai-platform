package ai.ultimate.memory;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for the memories table.
 *
 * Spring Data R2DBC generates implementations
 * for all methods automatically.
 *
 * Custom queries use @Query for:
 * - Top N memories by importance + access count
 * - Access count increment (atomic update)
 */
@Repository
public interface MemoryRepository
        extends R2dbcRepository<Memory, UUID> {

    /**
     * All memories for a user, most important first.
     * Used by: MemoryService.getAll()
     */
    Flux<Memory> findByUserIdOrderByImportanceDesc(
            UUID userId);

    /**
     * Memories filtered by type for a user.
     * Used by: MemoryService.getByType()
     * Example: get only PREFERENCE memories
     */
    Flux<Memory> findByUserIdAndTypeOrderByImportanceDesc(
            UUID userId, MemoryType type);

    /**
     * Count of memories for a user.
     * Used by: MemoryService.count()
     * Used by: CLI "memory list" header
     */
    Mono<Long> countByUserId(UUID userId);

    /**
     * Delete all memories for a user.
     * Used by: MemoryService.deleteAll()
     * Used by: CLI "memory clear" command
     */
    Mono<Void> deleteByUserId(UUID userId);

    /**
     * Check if a similar memory already exists.
     * Prevents duplicate memories from extraction.
     * Case-insensitive content match.
     */
    Mono<Boolean> existsByUserIdAndContentIgnoreCase(
            UUID userId, String content);

    /**
     * Top N memories by importance + access count.
     * Used for prompt injection — most relevant first.
     *
     * ORDER BY:
     * 1. importance DESC (highest priority first)
     * 2. access_count DESC (most used first)
     * 3. created_at DESC (newest first for ties)
     *
     * LIMIT: configurable (default 5 for prompts)
     */
    @Query("""
            SELECT * FROM memories
            WHERE user_id = :userId
            ORDER BY importance DESC,
                     access_count DESC,
                     created_at DESC
            LIMIT :limit
            """)
    Flux<Memory> findTopMemoriesByUserId(
            UUID userId, int limit);

    /**
     * Increment access count when memory is used.
     * Called each time a memory is injected into
     * a prompt. Atomic update — safe for concurrent use.
     */
    @Modifying
    @Query("""
            UPDATE memories
            SET access_count = access_count + 1,
                last_accessed = NOW(),
                updated_at = NOW()
            WHERE id = :memoryId
            """)
    Mono<Integer> incrementAccessCount(UUID memoryId);

    /**
     * Find memory by ID and verify ownership.
     * Used by: MemoryService.delete() for security.
     * Users can only delete their own memories.
     */
    Mono<Memory> findByIdAndUserId(
            UUID id, UUID userId);
}