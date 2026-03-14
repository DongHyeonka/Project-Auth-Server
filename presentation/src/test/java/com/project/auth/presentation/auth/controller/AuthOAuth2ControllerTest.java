package com.project.auth.presentation.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthOAuth2ControllerTest {

    private final AuthOAuth2Controller controller = new AuthOAuth2Controller();

    @Test
    void loginWithGoogleRedirectsToKeycloakAuthorizationEndpoint() {
        ResponseEntity<Void> response = controller.loginWithGoogle();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/oauth2/authorization/keycloak-google");
    }

    @Test
    void loginWithGithubRedirectsToKeycloakAuthorizationEndpoint() {
        ResponseEntity<Void> response = controller.loginWithGithub();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/oauth2/authorization/keycloak-github");
    }
}
