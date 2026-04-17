package com.project.auth.application.auth.resource;

public interface SyncKeycloakUserUseCase {

    SyncedKeycloakUser sync(KeycloakUserClaims claims);
}
