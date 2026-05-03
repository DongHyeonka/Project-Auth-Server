package com.project.auth.application.auth.identity;

import com.project.auth.domain.user.model.User;

import java.util.UUID;

public record LoadedKeycloakUser(
        UUID userId,
        String email,
        String name,
        String provider,
        String providerSubject
) {

    public static LoadedKeycloakUser from(User user) {
        return new LoadedKeycloakUser(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider().name(),
                user.getProviderSubject()
        );
    }
}
