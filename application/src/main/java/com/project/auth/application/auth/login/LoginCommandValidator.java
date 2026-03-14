package com.project.auth.application.auth.login;

import com.project.auth.application.auth.exception.InvalidUserCredentialsException;
import com.project.auth.domain.user.exception.InvalidUserEmailException;
import com.project.auth.domain.user.model.UserEmail;

final class LoginCommandValidator {

    private LoginCommandValidator() {
    }

    static ValidatedLoginCommand validate(LoginCommand command) {
        return new ValidatedLoginCommand(parseEmail(command.email()), validatePassword(command.password()));
    }

    private static UserEmail parseEmail(String email) {
        try {
            return UserEmail.from(email);
        } catch (InvalidUserEmailException exception) {
            throw new InvalidUserCredentialsException();
        }
    }

    private static String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidUserCredentialsException();
        }

        return password;
    }
}
