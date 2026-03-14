package com.project.auth.application.auth.login;

import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;

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

    public static LoginResult from(User user, IssuedAccessToken issuedAccessToken) {
        return new LoginResult(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider().name(),
                issuedAccessToken.accessToken(),
                issuedAccessToken.tokenType(),
                issuedAccessToken.expiresIn(),
                issuedAccessToken.issuedAt(),
                issuedAccessToken.expiresAt()
        );
    }
}
