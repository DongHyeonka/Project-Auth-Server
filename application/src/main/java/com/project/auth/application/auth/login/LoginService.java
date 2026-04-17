package com.project.auth.application.auth.login;

import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.domain.user.model.User;

import java.util.Objects;

public class LoginService implements LoginUseCase {

    private final LoginAuthenticationService loginAuthenticationService;
    private final IssueLoginTokenPort issueLoginTokenPort;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public LoginService(
            LoginAuthenticationService loginAuthenticationService,
            IssueLoginTokenPort issueLoginTokenPort,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        this.loginAuthenticationService = Objects.requireNonNull(
                loginAuthenticationService,
                "loginAuthenticationService must not be null"
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
        User user = loginAuthenticationService.authenticate(validatedCommand);

        LoginResult result = LoginResult.from(user, issueLoginTokenPort.issue(user));
        authAuditEventPublisher.publish(AuthAuditEvent.info(
                AuthAuditEventType.LOGIN_SUCCESS.code(),
                AuthAuditFields.USER_ID_HASH, AuthAuditFields.userIdHash(user.getId()),
                AuthAuditFields.EMAIL_MASKED, AuthAuditFields.maskedEmail(validatedCommand.email())
        ));
        return result;
    }
}
