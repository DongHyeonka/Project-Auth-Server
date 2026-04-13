package com.project.auth.application.auth.oauth.login;

import com.project.auth.application.auth.exception.OAuthAccountConflictException;
import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.LoadOAuthUserPort;
import com.project.auth.application.auth.oauth.login.port.out.RegisterOAuthUserPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
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
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public OAuthLoginService(
            LoadOAuthUserPort loadOAuthUserPort,
            RegisterOAuthUserPort registerOAuthUserPort,
            IssueOAuthLoginTokenPort issueOAuthLoginTokenPort,
            Clock clock,
            AuthAuditEventPublisher authAuditEventPublisher
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
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
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
        authAuditEventPublisher.publish(AuthAuditEvent.info(
                "OAUTH_LOGIN_SUCCESS",
                "userIdHash", AuthAuditFields.userIdHash(user.getId()),
                "emailMasked", AuthAuditFields.maskedEmail(validatedCommand.email()),
                "provider", user.getProvider()
        ));
        return result;
    }

    private User registerOAuthUser(ValidatedOAuthLoginCommand validatedCommand) {
        if (loadOAuthUserPort.existsByEmail(validatedCommand.email())) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    "OAUTH_LOGIN_FAILURE",
                    "emailMasked", AuthAuditFields.maskedEmail(validatedCommand.email()),
                    "provider", validatedCommand.provider(),
                    "reason", "email_conflict"
            ));
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
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    "OAUTH_LOGIN_FAILURE",
                    "emailMasked", AuthAuditFields.maskedEmail(validatedCommand.email()),
                    "provider", validatedCommand.provider(),
                    "reason", "duplicate_email_race_condition"
            ));
            throw new OAuthAccountConflictException();
        }
    }
}
