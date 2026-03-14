package com.project.auth.application.auth.oauth.login;

import com.project.auth.application.auth.exception.InvalidOAuthUserInfoException;
import com.project.auth.application.auth.exception.OAuthAccountConflictException;
import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.LoadOAuthUserPort;
import com.project.auth.application.auth.oauth.login.port.out.RegisterOAuthUserPort;
import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthLoginServiceTest {

    private OAuthLoginService oAuthLoginService;

    @BeforeEach
    void setUp() {
        FakeOAuthUserStore fakeOAuthUserStore = new FakeOAuthUserStore();
        IssueOAuthLoginTokenPort issueOAuthLoginTokenPort = user -> new IssuedAccessToken(
                "oauth-access-token",
                "Bearer",
                1800L,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:30:00Z")
        );
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-14T00:00:00Z"), ZoneOffset.UTC);

        oAuthLoginService = new OAuthLoginService(
                fakeOAuthUserStore,
                fakeOAuthUserStore,
                issueOAuthLoginTokenPort,
                fixedClock
        );
    }

    @Test
    void loginRegistersNewGoogleUserAndIssuesInternalToken() {
        LoginResult result = oAuthLoginService.login(new OAuthLoginCommand(
                AuthProvider.GOOGLE,
                "google-subject-001",
                "google-user@example.com",
                "구글유저"
        ));

        assertThat(result.provider()).isEqualTo("GOOGLE");
        assertThat(result.email()).isEqualTo("google-user@example.com");
        assertThat(result.accessToken()).isEqualTo("oauth-access-token");
    }

    @Test
    void loginReturnsExistingOAuthUserWhenProviderSubjectMatches() {
        LoginResult firstLogin = oAuthLoginService.login(new OAuthLoginCommand(
                AuthProvider.GITHUB,
                "github-subject-001",
                "github-user@example.com",
                "깃허브유저"
        ));

        LoginResult secondLogin = oAuthLoginService.login(new OAuthLoginCommand(
                AuthProvider.GITHUB,
                "github-subject-001",
                "github-user@example.com",
                "깃허브유저"
        ));

        assertThat(secondLogin.userId()).isEqualTo(firstLogin.userId());
        assertThat(secondLogin.provider()).isEqualTo("GITHUB");
    }

    @Test
    void loginRejectsDuplicateEmailOwnedByAnotherAccount() {
        oAuthLoginService.login(new OAuthLoginCommand(
                AuthProvider.GOOGLE,
                "google-subject-001",
                "duplicate@example.com",
                "구글유저"
        ));

        assertThatThrownBy(() -> oAuthLoginService.login(new OAuthLoginCommand(
                AuthProvider.GITHUB,
                "github-subject-001",
                "duplicate@example.com",
                "깃허브유저"
        ))).isInstanceOf(OAuthAccountConflictException.class);
    }

    @Test
    void loginRejectsInvalidOAuthUserInfo() {
        assertThatThrownBy(() -> oAuthLoginService.login(new OAuthLoginCommand(
                AuthProvider.GOOGLE,
                "",
                "not-an-email",
                "A"
        ))).isInstanceOf(InvalidOAuthUserInfoException.class);
    }

    private static final class FakeOAuthUserStore implements LoadOAuthUserPort, RegisterOAuthUserPort {

        private final Map<String, User> usersByProviderSubject = new ConcurrentHashMap<>();
        private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();

        @Override
        public Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject) {
            return Optional.ofNullable(usersByProviderSubject.get(provider.name() + ":" + providerSubject));
        }

        @Override
        public boolean existsByEmail(UserEmail email) {
            return usersByEmail.containsKey(email.value());
        }

        @Override
        public User save(User user) {
            usersByEmail.put(user.getEmail(), user);
            usersByProviderSubject.put(user.getProvider().name() + ":" + user.getProviderSubject(), user);
            return user;
        }
    }
}
