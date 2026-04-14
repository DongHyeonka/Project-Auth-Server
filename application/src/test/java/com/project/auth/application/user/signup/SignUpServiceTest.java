package com.project.auth.application.user.signup;

import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.application.user.exception.InvalidUserSignUpException;
import com.project.auth.application.user.signup.port.out.PasswordHasherPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignUpServiceTest {

    private SignUpService signUpService;
    private RecordingAuthAuditEventPublisher auditEventPublisher;

    @BeforeEach
    void setUp() {
        RegisterUserPort registerUserPort = new FakeUserRepository();
        PasswordHasherPort passwordHasherPort = rawPassword -> "encoded-" + rawPassword;
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneOffset.UTC);
        auditEventPublisher = new RecordingAuthAuditEventPublisher();

        signUpService = new SignUpService(registerUserPort, passwordHasherPort, fixedClock, auditEventPublisher);
    }

    @Test
    void signUpCreatesLocalUser() {
        SignUpCommand command = new SignUpCommand("tester@example.com", "password123", "테스터");

        var result = signUpService.signUp(command);

        assertThat(result.email()).isEqualTo("tester@example.com");
        assertThat(result.name()).isEqualTo("테스터");
        assertThat(result.provider()).isEqualTo("LOCAL");
        assertThat(result.registeredAt()).isEqualTo(Instant.parse("2026-03-13T00:00:00Z"));
        assertThat(auditEventPublisher.events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType()).isEqualTo("SIGNUP_SUCCESS");
                    assertThat(event.fields())
                            .containsEntry("emailMasked", "te***@example.com")
                            .containsKey("userIdHash")
                            .doesNotContainEntry("email", "tester@example.com");
                });
    }

    @Test
    void signUpRejectsDuplicateEmail() {
        SignUpCommand command = new SignUpCommand("tester@example.com", "password123", "테스터");

        signUpService.signUp(command);

        assertThatThrownBy(() -> signUpService.signUp(command))
                .isInstanceOf(DuplicateUserEmailException.class);
        assertThat(auditEventPublisher.events.getLast().eventType()).isEqualTo("SIGNUP_FAILURE");
        assertThat(auditEventPublisher.events.getLast().fields()).containsEntry("reason", "duplicate_email");
    }

    @Test
    void signUpRejectsInvalidEmailWithBusinessErrorCode() {
        SignUpCommand command = new SignUpCommand("invalid-email", "password123", "테스터");

        assertThatThrownBy(() -> signUpService.signUp(command))
                .isInstanceOf(InvalidUserSignUpException.class)
                .extracting(exception -> ((InvalidUserSignUpException) exception).getErrorCode().code())
                .isEqualTo("USER-001");
    }

    private static final class RecordingAuthAuditEventPublisher implements AuthAuditEventPublisher {

        private final List<AuthAuditEvent> events = new ArrayList<>();

        @Override
        public void publish(AuthAuditEvent event) {
            events.add(event);
        }
    }

    private static final class FakeUserRepository implements RegisterUserPort {

        private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();

        @Override
        public boolean existsByEmail(UserEmail email) {
            return usersByEmail.containsKey(email.value());
        }

        @Override
        public User save(User user) {
            usersByEmail.put(user.getEmail(), user);
            return user;
        }
    }
}
