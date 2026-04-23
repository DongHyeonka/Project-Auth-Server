package com.project.auth.application.auth.identity;

public record KeycloakUserClaims(
        String subject,
        String email,
        String name
) {
}
