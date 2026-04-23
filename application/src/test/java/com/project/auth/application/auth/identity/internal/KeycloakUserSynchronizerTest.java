package com.project.auth.application.auth.identity.internal;

import com.project.auth.application.auth.exception.InvalidKeycloakClaimsException;
import com.project.auth.application.auth.exception.KeycloakAccountConflictException;
import com.project.auth.application.auth.identity.KeycloakUserClaims;
import com.project.auth.application.auth.identity.SyncedKeycloakUser;
import com.project.auth.application.auth.identity.port.out.LoadKeycloakUserPort;
import com.project.auth.application.auth.identity.port.out.RegisterKeycloakUserPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakUserSynchronizerTest {

    private FakeKeycloakUserRepository userRepository;
    private RecordingAuthAuditEventPublisher auditEventPublisher;
    private KeycloakUserSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        userRepository = new FakeKeycloakUserRepository();
        auditEventPublisher = new RecordingAuthAuditEventPublisher();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-17T00:00:00Z"), ZoneOffset.UTC);
        synchronizer = new KeycloakUserSynchronizer(
                userRepository,
                userRepository,
                fixedClock,
                auditEventPublisher
        );
    }

    @Test
    void syncRegistersNewKeycloakUserFromVerifiedClaims() {
        SyncedKeycloakUser user = synchronizer.sync(new KeycloakUserClaims(
                "keycloak-subject-1",
                "Tester@Example.com",
                "테스터"
        ));

        assertThat(user.email()).isEqualTo("tester@example.com");
        assertThat(user.name()).isEqualTo("테스터");
        assertThat(user.provider()).isEqualTo(AuthProvider.KEYCLOAK.name());
        assertThat(user.providerSubject()).isEqualTo("keycloak-subject-1");
        assertThat(auditEventPublisher.events)
                .singleElement()
                .satisfies(event -> assertThat(event.eventType()).isEqualTo("KEYCLOAK_USER_SYNC_SUCCESS"));
    }

    @Test
    void syncReturnsExistingUserByKeycloakSubject() {
        SyncedKeycloakUser first = synchronizer.sync(new KeycloakUserClaims(
                "keycloak-subject-1",
                "tester@example.com",
                "테스터"
        ));

        SyncedKeycloakUser second = synchronizer.sync(new KeycloakUserClaims(
                "keycloak-subject-1",
                "changed@example.com",
                "변경"
        ));

        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(userRepository.usersBySubject).hasSize(1);
    }

    @Test
    void syncRejectsEmailConflictWithExistingInternalUser() {
        userRepository.emailConflicts.add("tester@example.com");

        assertThatThrownBy(() -> synchronizer.sync(new KeycloakUserClaims(
                "keycloak-subject-1",
                "tester@example.com",
                "테스터"
        ))).isInstanceOf(KeycloakAccountConflictException.class);
        assertThat(auditEventPublisher.events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo("KEYCLOAK_USER_SYNC_CONFLICT");
                    assertThat(event.fields()).containsEntry("reason", "email_conflict");
                });
    }

    @Test
    void syncRejectsBlankClaimsAsInvalid() {
        assertThatThrownBy(() -> synchronizer.sync(new KeycloakUserClaims(
                "keycloak-subject-1",
                "",
                "테스터"
        ))).isInstanceOf(InvalidKeycloakClaimsException.class);
    }

    @Test
    void syncRejectsMalformedEmailClaimAsInvalid() {
        assertThatThrownBy(() -> synchronizer.sync(new KeycloakUserClaims(
                "keycloak-subject-1",
                "not-an-email",
                "테스터"
        ))).isInstanceOf(InvalidKeycloakClaimsException.class);
    }

    private static final class FakeKeycloakUserRepository implements LoadKeycloakUserPort, RegisterKeycloakUserPort {

        private final Map<String, User> usersBySubject = new ConcurrentHashMap<>();
        private final List<String> emailConflicts = new ArrayList<>();

        @Override
        public boolean existsByEmail(String email) {
            return emailConflicts.contains(email);
        }

        @Override
        public Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject) {
            if (provider != AuthProvider.KEYCLOAK) {
                return Optional.empty();
            }
            return Optional.ofNullable(usersBySubject.get(providerSubject));
        }

        @Override
        public User save(User user) {
            usersBySubject.put(user.getProviderSubject(), user);
            return user;
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
