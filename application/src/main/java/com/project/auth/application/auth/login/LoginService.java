package com.project.auth.application.auth.login;

import com.project.auth.application.auth.exception.InvalidUserCredentialsException;
import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class LoginService implements LoginUseCase {

    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    private final LoadLoginUserPort loadLoginUserPort;
    private final PasswordVerifierPort passwordVerifierPort;
    private final IssueLoginTokenPort issueLoginTokenPort;

    public LoginService(
            LoadLoginUserPort loadLoginUserPort,
            PasswordVerifierPort passwordVerifierPort,
            IssueLoginTokenPort issueLoginTokenPort
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
    }

    @Override
    public LoginResult login(LoginCommand command) {
        ValidatedLoginCommand validatedCommand = LoginCommandValidator.validate(command);

        User user = loadLoginUserPort.findByEmail(validatedCommand.email())
                .orElseThrow(() -> {
                    audit.warn("LOGIN_FAILURE email={} reason=user_not_found", validatedCommand.email());
                    return new InvalidUserCredentialsException();
                });

        if (user.getProvider() != AuthProvider.LOCAL) {
            audit.warn("LOGIN_FAILURE email={} reason=non_local_provider provider={}",
                    validatedCommand.email(), user.getProvider());
            throw new InvalidUserCredentialsException();
        }

        if (!passwordVerifierPort.matches(validatedCommand.password(), user.getEncodedPassword())) {
            audit.warn("LOGIN_FAILURE email={} reason=invalid_password", validatedCommand.email());
            throw new InvalidUserCredentialsException();
        }

        LoginResult result = LoginResult.from(user, issueLoginTokenPort.issue(user));
        audit.info("LOGIN_SUCCESS userId={} email={}", user.getId(), user.getEmail());
        return result;
    }
}
