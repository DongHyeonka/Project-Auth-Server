package com.project.auth.presentation.auth.dto;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUserResponse(
        UUID userId,
        String subject,
        String email,
        String name,
        String provider,
        Set<String> authorities
) {

    public AuthenticatedUserResponse {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }
}
