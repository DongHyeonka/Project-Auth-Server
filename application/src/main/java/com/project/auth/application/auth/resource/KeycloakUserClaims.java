package com.project.auth.application.auth.resource;

public record KeycloakUserClaims(
        String subject,
        String email,
        String name
) {
}
