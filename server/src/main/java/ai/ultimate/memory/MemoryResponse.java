package ai.ultimate.memory;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for returning memories via REST API.
 * Does NOT include embedding vector
 * (too large for API responses).
 */
public record MemoryResponse(
        UUID id,
        MemoryType type,
        String content,
        Double importance,
        int accessCount,
        Instant lastAccessed,
        Instant createdAt
) {}