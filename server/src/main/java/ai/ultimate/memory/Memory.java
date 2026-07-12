package ai.ultimate.memory;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Long-term memory entity.
 * Maps to the 'memories' table (V9 migration).
 *
 * Each memory represents one fact Jarvis has
 * learned about a user across any session.
 *
 * Memories are:
 * - Injected into prompts (most important first)
 * - Searchable by semantic similarity (pgvector)
 * - Automatically extracted from conversations
 * - Manually addable via CLI commands
 */
@Table("memories")
public record Memory(

        @Id
        UUID id,

        @Column("user_id")
        UUID userId,

        MemoryType type,

        String content,

        @Column("source_session")
        UUID sourceSession,

        Double importance,

        @Column("access_count")
        int accessCount,

        @Column("last_accessed")
        Instant lastAccessed,

        @Column("created_at")
        Instant createdAt,

        @Column("updated_at")
        Instant updatedAt

) {
    /**
     * Create a new memory with default values.
     * Used when extracting memories from conversations.
     *
     * @param userId        owner of this memory
     * @param type          FACT/GOAL/PREFERENCE/CONTEXT/EVENT
     * @param content       the actual memory text
     * @param sourceSession which chat session this came from
     */
    public static Memory create(
            UUID userId,
            MemoryType type,
            String content,
            UUID sourceSession) {
        return new Memory(
                UUID.randomUUID(),
                userId,
                type,
                content,
                sourceSession,
                0.50,           // default importance
                0,              // never accessed yet
                null,           // never accessed yet
                Instant.now(),
                Instant.now()
        );
    }

    /**
     * Create a memory without session reference.
     * Used when manually adding via CLI command.
     *
     * @param userId  owner of this memory
     * @param type    FACT/GOAL/PREFERENCE/CONTEXT/EVENT
     * @param content the actual memory text
     */
    public static Memory createManual(
            UUID userId,
            MemoryType type,
            String content) {
        return create(userId, type, content, null);
    }

    /**
     * Create a copy with incremented access count.
     * Called each time this memory is used in a prompt.
     * Higher access count = more relevant to user.
     */
    public Memory withAccessed() {
        return new Memory(
                id, userId, type, content,
                sourceSession, importance,
                accessCount + 1,
                Instant.now(),    // update lastAccessed
                createdAt,
                Instant.now()     // update updatedAt
        );
    }

    /**
     * Create a copy with updated importance score.
     * Used by importance decay/boost algorithms.
     *
     * @param newImportance 0.0 (trivial) to 1.0 (critical)
     */
    public Memory withImportance(double newImportance) {
        return new Memory(
                id, userId, type, content,
                sourceSession, newImportance,
                accessCount, lastAccessed,
                createdAt, Instant.now()
        );
    }
}