package com.project.auth.application.user.signup;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignUpServiceTest {

    private SignUpService signUpService;

    @BeforeEach
    void setUp() {
        RegisterUserPort registerUserPort = new FakeUserRepository();
        PasswordHasherPort passwordHasherPort = rawPassword -> "encoded-" + rawPassword;
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneOffset.UTC);

        signUpService = new SignUpService(registerUserPort, passwordHasherPort, fixedClock);
    }

    @Test
    void signUpCreatesLocalUser() {
        SignUpCommand command = new SignUpCommand("tester@example.com", "password123", "테스터");

        var result = signUpService.signUp(command);

        assertThat(result.email()).isEqualTo("tester@example.com");
        assertThat(result.name()).isEqualTo("테스터");
        assertThat(result.provider()).isEqualTo("LOCAL");
        assertThat(result.registeredAt()).isEqualTo(Instant.parse("2026-03-13T00:00:00Z"));
    }

    @Test
    void signUpRejectsDuplicateEmail() {
        SignUpCommand command = new SignUpCommand("tester@example.com", "password123", "테스터");

        signUpService.signUp(command);

        assertThatThrownBy(() -> signUpService.signUp(command))
                .isInstanceOf(DuplicateUserEmailException.class);
    }

    @Test
    void signUpRejectsInvalidEmailWithBusinessErrorCode() {
        SignUpCommand command = new SignUpCommand("invalid-email", "password123", "테스터");

        assertThatThrownBy(() -> signUpService.signUp(command))
                .isInstanceOf(InvalidUserSignUpException.class)
                .extracting(exception -> ((InvalidUserSignUpException) exception).getErrorCode().code())
                .isEqualTo("USER-001");
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
