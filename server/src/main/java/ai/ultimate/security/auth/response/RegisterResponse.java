package ai.ultimate.security.auth.response;

import ai.ultimate.user.UserRole;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String username,
        String displayName,
        UserRole role,
        String message
) {}
