package com.project.auth.application.auth.identity;

public interface LoadKeycloakUserUseCase {

    LoadedKeycloakUser load(KeycloakUserClaims claims);
}
