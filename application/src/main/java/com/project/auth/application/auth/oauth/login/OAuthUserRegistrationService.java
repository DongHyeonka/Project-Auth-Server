package com.project.auth.application.auth.oauth.login;

import com.project.auth.application.auth.exception.OAuthAccountConflictException;
import com.project.auth.application.auth.oauth.login.port.out.LoadOAuthUserPort;
import com.project.auth.application.auth.oauth.login.port.out.RegisterOAuthUserPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFailureReason;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.domain.user.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class OAuthUserRegistrationService {

    private final LoadOAuthUserPort loadOAuthUserPort;
    private final RegisterOAuthUserPort registerOAuthUserPort;
    private final Clock clock;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public OAuthUserRegistrationService(
            LoadOAuthUserPort loadOAuthUserPort,
            RegisterOAuthUserPort registerOAuthUserPort,
            Clock clock,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        this.loadOAuthUserPort = Objects.requireNonNull(loadOAuthUserPort, "loadOAuthUserPort must not be null");
        this.registerOAuthUserPort = Objects.requireNonNull(
                registerOAuthUserPort,
                "registerOAuthUserPort must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
    }

    @Transactional
    public User findOrRegister(ValidatedOAuthLoginCommand command) {
        return loadOAuthUserPort.findByProviderAndProviderSubject(command.provider(), command.providerSubject())
                .orElseGet(() -> registerOAuthUser(command));
    }

    private User registerOAuthUser(ValidatedOAuthLoginCommand command) {
        if (loadOAuthUserPort.existsByEmail(command.email())) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    AuthAuditEventType.OAUTH_LOGIN_FAILURE.code(),
                    "emailMasked", AuthAuditFields.maskedEmail(command.email()),
                    "provider", command.provider(),
                    "reason", AuthAuditFailureReason.EMAIL_CONFLICT.code()
            ));
            throw new OAuthAccountConflictException();
        }

        User user = User.registerSocial(
                UUID.randomUUID(),
                command.email(),
                command.name(),
                command.provider(),
                command.providerSubject(),
                Instant.now(clock)
        );

        try {
            return registerOAuthUserPort.save(user);
        } catch (DuplicateUserEmailException exception) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    AuthAuditEventType.OAUTH_LOGIN_FAILURE.code(),
                    "emailMasked", AuthAuditFields.maskedEmail(command.email()),
                    "provider", command.provider(),
                    "reason", AuthAuditFailureReason.DUPLICATE_EMAIL_RACE_CONDITION.code()
            ));
            throw new OAuthAccountConflictException();
        }
    }
}
