package com.project.auth.application.auth.identity;

public interface SyncKeycloakUserUseCase {

    SyncedKeycloakUser sync(KeycloakUserClaims claims);
}
