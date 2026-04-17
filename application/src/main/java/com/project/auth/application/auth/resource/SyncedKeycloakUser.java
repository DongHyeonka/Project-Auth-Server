package com.project.auth.application.auth.resource;

import com.project.auth.domain.user.model.User;

import java.util.UUID;

public record SyncedKeycloakUser(
        UUID userId,
        String email,
        String name,
        String provider,
        String providerSubject
) {

    public static SyncedKeycloakUser from(User user) {
        return new SyncedKeycloakUser(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider().name(),
                user.getProviderSubject()
        );
    }
}
