package com.project.auth.application.auth.token;

import java.time.Instant;

public record IssuedAccessToken(
        String issuer,
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant issuedAt,
        Instant expiresAt
) {
}
