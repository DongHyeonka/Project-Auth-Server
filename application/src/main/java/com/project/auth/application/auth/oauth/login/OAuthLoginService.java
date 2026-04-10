package com.project.auth.application.auth.oauth.login;

import com.project.auth.application.auth.exception.OAuthAccountConflictException;
import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.LoadOAuthUserPort;
import com.project.auth.application.auth.oauth.login.port.out.RegisterOAuthUserPort;
import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.domain.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class OAuthLoginService implements OAuthLoginUseCase {

    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    private final LoadOAuthUserPort loadOAuthUserPort;
    private final RegisterOAuthUserPort registerOAuthUserPort;
    private final IssueOAuthLoginTokenPort issueOAuthLoginTokenPort;
    private final Clock clock;

    public OAuthLoginService(
            LoadOAuthUserPort loadOAuthUserPort,
            RegisterOAuthUserPort registerOAuthUserPort,
            IssueOAuthLoginTokenPort issueOAuthLoginTokenPort,
            Clock clock
    ) {
        this.loadOAuthUserPort = Objects.requireNonNull(loadOAuthUserPort, "loadOAuthUserPort must not be null");
        this.registerOAuthUserPort = Objects.requireNonNull(
                registerOAuthUserPort,
                "registerOAuthUserPort must not be null"
        );
        this.issueOAuthLoginTokenPort = Objects.requireNonNull(
                issueOAuthLoginTokenPort,
                "issueOAuthLoginTokenPort must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public LoginResult login(OAuthLoginCommand command) {
        ValidatedOAuthLoginCommand validatedCommand = OAuthLoginCommandValidator.validate(command);
        User user = loadOAuthUserPort.findByProviderAndProviderSubject(
                        validatedCommand.provider(),
                        validatedCommand.providerSubject()
                )
                .orElseGet(() -> registerOAuthUser(validatedCommand));

        LoginResult result = LoginResult.from(user, issueOAuthLoginTokenPort.issue(user));
        audit.info("OAUTH_LOGIN_SUCCESS userId={} email={} provider={}",
                user.getId(), user.getEmail(), user.getProvider());
        return result;
    }

    private User registerOAuthUser(ValidatedOAuthLoginCommand validatedCommand) {
        if (loadOAuthUserPort.existsByEmail(validatedCommand.email())) {
            audit.warn("OAUTH_LOGIN_FAILURE email={} provider={} reason=email_conflict",
                    validatedCommand.email(), validatedCommand.provider());
            throw new OAuthAccountConflictException();
        }

        User user = User.registerSocial(
                UUID.randomUUID(),
                validatedCommand.email(),
                validatedCommand.name(),
                validatedCommand.provider(),
                validatedCommand.providerSubject(),
                Instant.now(clock)
        );

        try {
            return registerOAuthUserPort.save(user);
        } catch (DuplicateUserEmailException exception) {
            audit.warn("OAUTH_LOGIN_FAILURE email={} provider={} reason=duplicate_email_race_condition",
                    validatedCommand.email(), validatedCommand.provider());
            throw new OAuthAccountConflictException();
        }
    }
}
