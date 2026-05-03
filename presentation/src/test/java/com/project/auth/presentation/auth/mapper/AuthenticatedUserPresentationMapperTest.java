package com.project.auth.presentation.auth.mapper;

import com.project.auth.application.auth.identity.LoadedKeycloakUser;
import com.project.auth.presentation.auth.dto.AuthenticatedUserResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserPresentationMapperTest {

    private final AuthenticatedUserPresentationMapper mapper = new AuthenticatedUserPresentationMapper();

    @Test
    void toResponseMergesSynchronizedUserWithTokenAuthorities() {
        LoadedKeycloakUser synced = new LoadedKeycloakUser(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "tester@example.com",
                "테스터",
                "KEYCLOAK",
                "keycloak-subject-1"
        );

        AuthenticatedUserResponse response = mapper.toResponse(
                synced,
                new LinkedHashSet<>(Set.of("ROLE_user", "SCOPE_openid"))
        );

        assertThat(response.userId()).isEqualTo(synced.userId());
        assertThat(response.subject()).isEqualTo("keycloak-subject-1");
        assertThat(response.email()).isEqualTo("tester@example.com");
        assertThat(response.name()).isEqualTo("테스터");
        assertThat(response.provider()).isEqualTo("KEYCLOAK");
        assertThat(response.authorities()).containsExactlyInAnyOrder("ROLE_user", "SCOPE_openid");
    }

    @Test
    void toResponseProducesEmptyAuthoritiesWhenNullGiven() {
        LoadedKeycloakUser synced = new LoadedKeycloakUser(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "tester@example.com",
                "테스터",
                "KEYCLOAK",
                "keycloak-subject-1"
        );

        AuthenticatedUserResponse response = mapper.toResponse(synced, null);

        assertThat(response.authorities()).isEmpty();
    }
}
