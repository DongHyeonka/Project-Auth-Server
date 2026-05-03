package com.project.auth.presentation.auth.current;

import java.util.Set;

public record AuthenticatedUser(
        String subject,
        String email,
        String name,
        Set<String> authorities
) {

    public AuthenticatedUser {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }
}
