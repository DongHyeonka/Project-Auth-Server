package com.project.auth.application.auth.login;

import com.project.auth.application.auth.exception.InvalidUserCredentialsException;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginServiceTest {

    private LoginService loginService;

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
                "issued-access-token",
                "Bearer",
                1800L,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:30:00Z")
        );

        loginService = new LoginService(loadLoginUserPort, passwordVerifierPort, issueLoginTokenPort);
    }

    @Test
    void loginReturnsIssuedTokenForLocalUser() {
        LoginResult result = loginService.login(new LoginCommand("tester@example.com", "password123"));

        assertThat(result.email()).isEqualTo("tester@example.com");
        assertThat(result.accessToken()).isEqualTo("issued-access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(1800L);
    }

    @Test
    void loginRejectsInvalidPassword() {
        assertThatThrownBy(() -> loginService.login(new LoginCommand("tester@example.com", "wrong-password")))
                .isInstanceOf(InvalidUserCredentialsException.class);
    }

    @Test
    void loginRejectsUnknownEmail() {
        assertThatThrownBy(() -> loginService.login(new LoginCommand("unknown@example.com", "password123")))
                .isInstanceOf(InvalidUserCredentialsException.class);
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
