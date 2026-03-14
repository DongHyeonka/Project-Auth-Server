package com.project.auth.application.auth.oauth.login;

import com.project.auth.application.auth.exception.OAuthAccountConflictException;
import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.LoadOAuthUserPort;
import com.project.auth.application.auth.oauth.login.port.out.RegisterOAuthUserPort;
import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.domain.user.model.User;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class OAuthLoginService implements OAuthLoginUseCase {

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

        return LoginResult.from(user, issueOAuthLoginTokenPort.issue(user));
    }

    private User registerOAuthUser(ValidatedOAuthLoginCommand validatedCommand) {
        if (loadOAuthUserPort.existsByEmail(validatedCommand.email())) {
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
            throw new OAuthAccountConflictException();
        }
    }
}
