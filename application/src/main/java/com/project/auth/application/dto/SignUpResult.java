package com.project.auth.application.dto;

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
