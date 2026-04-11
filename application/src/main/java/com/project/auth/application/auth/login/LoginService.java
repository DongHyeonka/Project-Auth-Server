package com.project.auth.application.auth.login;

import com.project.auth.application.auth.exception.InvalidUserCredentialsException;
import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;

import java.util.Objects;

public class LoginService implements LoginUseCase {

    private final LoadLoginUserPort loadLoginUserPort;
    private final PasswordVerifierPort passwordVerifierPort;
    private final IssueLoginTokenPort issueLoginTokenPort;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public LoginService(
            LoadLoginUserPort loadLoginUserPort,
            PasswordVerifierPort passwordVerifierPort,
            IssueLoginTokenPort issueLoginTokenPort,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        this.loadLoginUserPort = Objects.requireNonNull(loadLoginUserPort, "loadLoginUserPort must not be null");
        this.passwordVerifierPort = Objects.requireNonNull(
                passwordVerifierPort,
                "passwordVerifierPort must not be null"
        );
        this.issueLoginTokenPort = Objects.requireNonNull(
                issueLoginTokenPort,
                "issueLoginTokenPort must not be null"
        );
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
    }

    @Override
    public LoginResult login(LoginCommand command) {
        ValidatedLoginCommand validatedCommand = LoginCommandValidator.validate(command);

        User user = loadLoginUserPort.findByEmail(validatedCommand.email())
                .orElseThrow(() -> {
                    authAuditEventPublisher.publish(AuthAuditEvent.warn(
                            "LOGIN_FAILURE",
                            "email", validatedCommand.email(),
                            "reason", "user_not_found"
                    ));
                    return new InvalidUserCredentialsException();
                });

        if (user.getProvider() != AuthProvider.LOCAL) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    "LOGIN_FAILURE",
                    "email", validatedCommand.email(),
                    "reason", "non_local_provider",
                    "provider", user.getProvider()
            ));
            throw new InvalidUserCredentialsException();
        }

        if (!passwordVerifierPort.matches(validatedCommand.password(), user.getEncodedPassword())) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    "LOGIN_FAILURE",
                    "email", validatedCommand.email(),
                    "reason", "invalid_password"
            ));
            throw new InvalidUserCredentialsException();
        }

        LoginResult result = LoginResult.from(user, issueLoginTokenPort.issue(user));
        authAuditEventPublisher.publish(AuthAuditEvent.info(
                "LOGIN_SUCCESS",
                "userId", user.getId(),
                "email", user.getEmail()
        ));
        return result;
    }
}
