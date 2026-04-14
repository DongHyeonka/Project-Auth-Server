package com.project.auth.application.auth.login;

import com.project.auth.application.auth.exception.InvalidUserCredentialsException;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFailureReason;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class LoginAuthenticationService {

    private final LoadLoginUserPort loadLoginUserPort;
    private final PasswordVerifierPort passwordVerifierPort;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public LoginAuthenticationService(
            LoadLoginUserPort loadLoginUserPort,
            PasswordVerifierPort passwordVerifierPort,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        this.loadLoginUserPort = Objects.requireNonNull(loadLoginUserPort, "loadLoginUserPort must not be null");
        this.passwordVerifierPort = Objects.requireNonNull(
                passwordVerifierPort,
                "passwordVerifierPort must not be null"
        );
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
    }

    @Transactional(readOnly = true)
    public User authenticate(ValidatedLoginCommand command) {
        User user = loadLoginUserPort.findByEmail(command.email())
                .orElseThrow(() -> {
                    authAuditEventPublisher.publish(AuthAuditEvent.warn(
                            AuthAuditEventType.LOGIN_FAILURE.code(),
                            "emailMasked", AuthAuditFields.maskedEmail(command.email()),
                            "reason", AuthAuditFailureReason.USER_NOT_FOUND.code()
                    ));
                    return new InvalidUserCredentialsException();
                });

        if (user.getProvider() != AuthProvider.LOCAL) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    AuthAuditEventType.LOGIN_FAILURE.code(),
                    "emailMasked", AuthAuditFields.maskedEmail(command.email()),
                    "reason", AuthAuditFailureReason.NON_LOCAL_PROVIDER.code(),
                    "provider", user.getProvider()
            ));
            throw new InvalidUserCredentialsException();
        }

        if (!passwordVerifierPort.matches(command.password(), user.getEncodedPassword())) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    AuthAuditEventType.LOGIN_FAILURE.code(),
                    "emailMasked", AuthAuditFields.maskedEmail(command.email()),
                    "reason", AuthAuditFailureReason.INVALID_PASSWORD.code()
            ));
            throw new InvalidUserCredentialsException();
        }

        return user;
    }
}
