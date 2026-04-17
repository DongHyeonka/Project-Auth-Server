package com.project.auth.application.auth.oauth.login;

import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.domain.user.model.User;

import java.util.Objects;

public class OAuthLoginService implements OAuthLoginUseCase {

    private final OAuthUserRegistrationService oAuthUserRegistrationService;
    private final IssueOAuthLoginTokenPort issueOAuthLoginTokenPort;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public OAuthLoginService(
            OAuthUserRegistrationService oAuthUserRegistrationService,
            IssueOAuthLoginTokenPort issueOAuthLoginTokenPort,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        this.oAuthUserRegistrationService = Objects.requireNonNull(
                oAuthUserRegistrationService,
                "oAuthUserRegistrationService must not be null"
        );
        this.issueOAuthLoginTokenPort = Objects.requireNonNull(
                issueOAuthLoginTokenPort,
                "issueOAuthLoginTokenPort must not be null"
        );
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
    }

    @Override
    public LoginResult login(OAuthLoginCommand command) {
        ValidatedOAuthLoginCommand validatedCommand = OAuthLoginCommandValidator.validate(command);
        User user = oAuthUserRegistrationService.findOrRegister(validatedCommand);

        LoginResult result = LoginResult.from(user, issueOAuthLoginTokenPort.issue(user));
        authAuditEventPublisher.publish(AuthAuditEvent.info(
                AuthAuditEventType.OAUTH_LOGIN_SUCCESS.code(),
                AuthAuditFields.USER_ID_HASH, AuthAuditFields.userIdHash(user.getId()),
                AuthAuditFields.EMAIL_MASKED, AuthAuditFields.maskedEmail(validatedCommand.email()),
                AuthAuditFields.PROVIDER, user.getProvider()
        ));
        return result;
    }
}
