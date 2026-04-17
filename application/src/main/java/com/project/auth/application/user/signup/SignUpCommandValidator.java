package com.project.auth.application.user.signup;

import com.project.auth.application.support.exception.UserErrorCode;
import com.project.auth.application.user.exception.InvalidUserSignUpException;
import com.project.auth.domain.user.exception.InvalidUserEmailException;
import com.project.auth.domain.user.exception.InvalidUserNameException;
import com.project.auth.domain.user.exception.InvalidUserPasswordException;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import com.project.auth.domain.user.service.UserPasswordPolicy;

final class SignUpCommandValidator {

    private SignUpCommandValidator() {
    }

    static ValidatedSignUpCommand validate(SignUpCommand command) {
        return new ValidatedSignUpCommand(
                parseEmail(command.email()),
                validateRawPassword(command.password()),
                parseName(command.name())
        );
    }

    private static UserEmail parseEmail(String email) {
        try {
            return UserEmail.from(email);
        } catch (InvalidUserEmailException exception) {
            throw new InvalidUserSignUpException(UserErrorCode.USER_EMAIL_INVALID, exception.getMessage());
        }
    }

    private static String validateRawPassword(String rawPassword) {
        try {
            UserPasswordPolicy.validateRaw(rawPassword);
            return rawPassword;
        } catch (InvalidUserPasswordException exception) {
            throw new InvalidUserSignUpException(UserErrorCode.USER_PASSWORD_INVALID, exception.getMessage());
        }
    }

    private static UserName parseName(String name) {
        try {
            return UserName.from(name);
        } catch (InvalidUserNameException exception) {
            throw new InvalidUserSignUpException(UserErrorCode.USER_NAME_INVALID, exception.getMessage());
        }
    }
}
