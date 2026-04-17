package com.project.auth.config.auth.security;

import com.project.auth.presentation.auth.current.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter(
            new KeycloakGrantedAuthoritiesConverter(),
            new KeycloakAuthenticatedUserFactory()
    );

    @Test
    void convertMapsKeycloakClaimsAndRealmRolesToProjectPrincipal() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081/realms/project-auth")
                .subject("keycloak-subject-1")
                .issuedAt(Instant.parse("2026-04-17T00:00:00Z"))
                .expiresAt(Instant.parse("2026-04-17T00:30:00Z"))
                .claim("email", "tester@example.com")
                .claim("name", "테스터")
                .claim("scope", "openid profile")
                .claim("realm_access", Map.of("roles", List.of("user", "admin")))
                .build();

        var authentication = converter.convert(jwt);

        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(principal.subject()).isEqualTo("keycloak-subject-1");
        assertThat(principal.email()).isEqualTo("tester@example.com");
        assertThat(principal.name()).isEqualTo("테스터");
        assertThat(principal.authorities()).contains("SCOPE_openid", "SCOPE_profile", "ROLE_user", "ROLE_admin");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("SCOPE_openid", "SCOPE_profile", "ROLE_user", "ROLE_admin");
    }
}
