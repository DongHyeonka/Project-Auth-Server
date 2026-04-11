package com.project.auth.application.auth.login;

import com.project.auth.application.auth.exception.InvalidUserCredentialsException;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginServiceTest {

    private LoginService loginService;
    private RecordingAuthAuditEventPublisher auditEventPublisher;

    @BeforeEach
    void setUp() {
        LoadLoginUserPort loadLoginUserPort = new FakeUserRepository();
        PasswordVerifierPort passwordVerifierPort = new PasswordVerifierPort() {
            @Override
            public boolean matches(String rawPassword, String encodedPassword) {
                return ("encoded-" + rawPassword).equals(encodedPassword);
            }
        };
        IssueLoginTokenPort issueLoginTokenPort = user -> new IssuedAccessToken(
                "project-auth-server",
                "issued-access-token",
                "Bearer",
                1800L,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:30:00Z")
        );
        auditEventPublisher = new RecordingAuthAuditEventPublisher();

        loginService = new LoginService(
                loadLoginUserPort,
                passwordVerifierPort,
                issueLoginTokenPort,
                auditEventPublisher
        );
    }

    @Test
    void loginReturnsIssuedTokenForLocalUser() {
        LoginResult result = loginService.login(new LoginCommand("tester@example.com", "password123"));

        assertThat(result.email()).isEqualTo("tester@example.com");
        assertThat(result.issuer()).isEqualTo("project-auth-server");
        assertThat(result.accessToken()).isEqualTo("issued-access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(1800L);
        assertThat(auditEventPublisher.events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN_SUCCESS");
                    assertThat(event.fields()).containsEntry("email", "tester@example.com");
                });
    }

    @Test
    void loginRejectsInvalidPassword() {
        assertThatThrownBy(() -> loginService.login(new LoginCommand("tester@example.com", "wrong-password")))
                .isInstanceOf(InvalidUserCredentialsException.class);
        assertThat(auditEventPublisher.events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN_FAILURE");
                    assertThat(event.fields()).containsEntry("reason", "invalid_password");
                });
    }

    @Test
    void loginRejectsUnknownEmail() {
        assertThatThrownBy(() -> loginService.login(new LoginCommand("unknown@example.com", "password123")))
                .isInstanceOf(InvalidUserCredentialsException.class);
        assertThat(auditEventPublisher.events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo("LOGIN_FAILURE");
                    assertThat(event.fields()).containsEntry("reason", "user_not_found");
                });
    }

    private static final class RecordingAuthAuditEventPublisher implements AuthAuditEventPublisher {

        private final List<AuthAuditEvent> events = new ArrayList<>();

        @Override
        public void publish(AuthAuditEvent event) {
            events.add(event);
        }
    }

    private static final class FakeUserRepository implements LoadLoginUserPort {

        private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();

        private FakeUserRepository() {
            User seededUser = User.registerLocal(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    UserEmail.from("tester@example.com"),
                    "encoded-password123",
                    UserName.from("테스터"),
                    Instant.parse("2026-03-13T00:00:00Z")
            );
            usersByEmail.put(seededUser.getEmail(), seededUser);
        }

        @Override
        public Optional<User> findByEmail(UserEmail email) {
            return Optional.ofNullable(usersByEmail.get(email.value()));
        }
    }
}
