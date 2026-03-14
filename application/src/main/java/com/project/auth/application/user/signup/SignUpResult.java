package com.project.auth.application.user.signup;

import java.time.Instant;
import java.util.UUID;

public record SignUpResult(
        UUID userId,
        String email,
        String name,
        String provider,
        Instant registeredAt
) {
}
