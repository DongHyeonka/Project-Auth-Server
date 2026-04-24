package com.project.auth.application.auth.identity.internal;

import com.project.auth.application.auth.exception.InvalidKeycloakClaimsException;
import com.project.auth.application.auth.exception.KeycloakUserNotFoundException;
import com.project.auth.application.auth.identity.KeycloakUserClaims;
import com.project.auth.application.auth.identity.LoadedKeycloakUser;
import com.project.auth.application.auth.identity.port.out.LoadKeycloakUserPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakUserLoaderTest {

    private FakeKeycloakUserRepository userRepository;
    private RecordingAuthAuditEventPublisher auditEventPublisher;
    private KeycloakUserLoader loader;

    @BeforeEach
    void setUp() {
        userRepository = new FakeKeycloakUserRepository();
        auditEventPublisher = new RecordingAuthAuditEventPublisher();
        loader = new KeycloakUserLoader(
                userRepository,
                auditEventPublisher
        );
    }

    @Test
    void loadReturnsExistingUserByKeycloakSubject() {
        userRepository.usersBySubject.put("keycloak-subject-1", User.registerKeycloak(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UserEmail.from("tester@example.com"),
                UserName.from("테스터"),
                "keycloak-subject-1",
                Instant.parse("2026-04-17T00:00:00Z")
        ));

        LoadedKeycloakUser user = loader.load(new KeycloakUserClaims(
                "keycloak-subject-1",
                "changed@example.com",
                "변경"
        ));

        assertThat(user.userId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(user.email()).isEqualTo("tester@example.com");
        assertThat(user.name()).isEqualTo("테스터");
        assertThat(user.provider()).isEqualTo(AuthProvider.KEYCLOAK.name());
        assertThat(user.providerSubject()).isEqualTo("keycloak-subject-1");
        assertThat(auditEventPublisher.events).isEmpty();
    }

    @Test
    void loadRejectsMissingInternalUser() {
        assertThatThrownBy(() -> loader.load(new KeycloakUserClaims(
                "keycloak-subject-1",
                "tester@example.com",
                "테스터"
        ))).isInstanceOf(KeycloakUserNotFoundException.class);
        assertThat(auditEventPublisher.events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo("KEYCLOAK_USER_NOT_FOUND");
                    assertThat(event.fields()).containsEntry("subject", "keycloak-subject-1");
                });
    }

    @Test
    void loadRejectsBlankClaimsAsInvalid() {
        assertThatThrownBy(() -> loader.load(new KeycloakUserClaims(
                "keycloak-subject-1",
                "",
                "테스터"
        ))).isInstanceOf(InvalidKeycloakClaimsException.class);
    }

    @Test
    void loadRejectsMalformedEmailClaimAsInvalid() {
        assertThatThrownBy(() -> loader.load(new KeycloakUserClaims(
                "keycloak-subject-1",
                "not-an-email",
                "테스터"
        ))).isInstanceOf(InvalidKeycloakClaimsException.class);
    }

    private static final class FakeKeycloakUserRepository implements LoadKeycloakUserPort {

        private final Map<String, User> usersBySubject = new ConcurrentHashMap<>();

        @Override
        public Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject) {
            if (provider != AuthProvider.KEYCLOAK) {
                return Optional.empty();
            }
            return Optional.ofNullable(usersBySubject.get(providerSubject));
        }
    }

    private static final class RecordingAuthAuditEventPublisher implements AuthAuditEventPublisher {

        private final List<AuthAuditEvent> events = new ArrayList<>();

        @Override
        public void publish(AuthAuditEvent event) {
            events.add(event);
        }
    }
}
