package com.project.auth.application.auth.login;

import java.time.Instant;
import java.util.UUID;

public record LoginResult(
        UUID userId,
        String email,
        String name,
        String provider,
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant issuedAt,
        Instant expiresAt
) {
}
