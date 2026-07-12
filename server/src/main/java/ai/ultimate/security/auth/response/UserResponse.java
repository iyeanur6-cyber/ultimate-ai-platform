package ai.ultimate.security.auth.response;

import ai.ultimate.user.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        UserRole role,
        boolean active,
        Instant createdAt
) {}
