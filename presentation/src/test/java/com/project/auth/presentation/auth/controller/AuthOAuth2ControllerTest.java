package com.project.auth.presentation.auth.controller;

import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.presentation.auth.current.OAuth2AuthorizationRedirects;
import com.project.auth.presentation.auth.current.OAuth2LoginPrincipal;
import com.project.auth.presentation.auth.mapper.AuthPresentationMapper;
import com.project.auth.presentation.auth.mapper.OAuth2AuthenticationCommandMapper;
import com.project.auth.presentation.support.response.FixedApiResultFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthOAuth2ControllerTest {

    private final AuthOAuth2Controller controller = new AuthOAuth2Controller(
            command -> new LoginResult(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    command.email(),
                    command.name(),
                    command.provider(),
                    "project-auth-server",
                    "issued-access-token",
                    "Bearer",
                    1800L,
                    Instant.parse("2026-03-14T00:00:00Z"),
                    Instant.parse("2026-03-14T00:30:00Z")
            ),
            new AuthPresentationMapper(),
            new OAuth2AuthenticationCommandMapper(),
            new TestOAuth2AuthorizationRedirects(),
            new FixedApiResultFactory()
    );

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

    @Test
    void completeOAuthLoginReturnsJwtResponse() {
        OAuth2LoginPrincipal currentUser = new OAuth2LoginPrincipal(
                "GOOGLE",
                "google-subject",
                "tester@example.com",
                "테스터"
        );

        var response = controller.completeOAuthLogin(currentUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().user().provider()).isEqualTo("GOOGLE");
        assertThat(response.getBody().data().user().email()).isEqualTo("tester@example.com");
        assertThat(response.getBody().data().token().issuer()).isEqualTo("project-auth-server");
    }

    private static class TestOAuth2AuthorizationRedirects implements OAuth2AuthorizationRedirects {

        @Override
        public String googleAuthorizationPath() {
            return "/oauth2/authorization/keycloak-google";
        }

        @Override
        public String githubAuthorizationPath() {
            return "/oauth2/authorization/keycloak-github";
        }
    }
}
