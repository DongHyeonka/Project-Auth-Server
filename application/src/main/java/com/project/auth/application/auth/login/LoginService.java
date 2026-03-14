package com.project.auth.application.auth.login;

import com.project.auth.application.auth.exception.InvalidUserCredentialsException;
import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.exception.InvalidUserEmailException;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;

import java.util.Objects;

public class LoginService implements LoginUseCase {

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
        UserEmail userEmail = parseUserEmail(command.email());
        User user = loadLoginUserPort.findByEmail(userEmail)
                .orElseThrow(InvalidUserCredentialsException::new);

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new InvalidUserCredentialsException();
        }

        if (!passwordVerifierPort.matches(command.password(), user.getEncodedPassword())) {
            throw new InvalidUserCredentialsException();
        }

        IssuedAccessToken issuedAccessToken = issueLoginTokenPort.issue(user);

        return new LoginResult(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider().name(),
                issuedAccessToken.accessToken(),
                issuedAccessToken.tokenType(),
                issuedAccessToken.expiresIn(),
                issuedAccessToken.issuedAt(),
                issuedAccessToken.expiresAt()
        );
    }

    private UserEmail parseUserEmail(String email) {
        try {
            return UserEmail.from(email);
        } catch (InvalidUserEmailException exception) {
            throw new InvalidUserCredentialsException();
        }
    }
}
