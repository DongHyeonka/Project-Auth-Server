package com.project.auth.application.user.signup;

import com.project.auth.domain.user.model.User;

import java.time.Instant;
import java.util.UUID;

public record SignUpResult(
        UUID userId,
        String email,
        String name,
        String provider,
        Instant registeredAt
) {

    public static SignUpResult from(User user) {
        return new SignUpResult(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider().name(),
                user.getCreatedAt()
        );
    }
}
