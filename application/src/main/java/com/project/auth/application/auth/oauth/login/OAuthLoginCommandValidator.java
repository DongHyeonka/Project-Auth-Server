package com.project.auth.application.auth.oauth.login;

import com.project.auth.application.auth.exception.InvalidOAuthUserInfoException;
import com.project.auth.domain.user.exception.InvalidUserEmailException;
import com.project.auth.domain.user.exception.InvalidUserNameException;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;

final class OAuthLoginCommandValidator {

    private OAuthLoginCommandValidator() {
    }

    static ValidatedOAuthLoginCommand validate(OAuthLoginCommand command) {
        return new ValidatedOAuthLoginCommand(
                validateProvider(command.provider()),
                validateProviderSubject(command.providerSubject()),
                parseEmail(command.email()),
                parseName(command.name())
        );
    }

    private static AuthProvider validateProvider(AuthProvider provider) {
        if (provider == null || provider == AuthProvider.LOCAL) {
            throw new InvalidOAuthUserInfoException();
        }

        return provider;
    }

    private static String validateProviderSubject(String providerSubject) {
        if (providerSubject == null || providerSubject.isBlank()) {
            throw new InvalidOAuthUserInfoException();
        }

        return providerSubject.trim();
    }

    private static UserEmail parseEmail(String email) {
        try {
            return UserEmail.from(email);
        } catch (InvalidUserEmailException exception) {
            throw new InvalidOAuthUserInfoException();
        }
    }

    private static UserName parseName(String name) {
        try {
            return UserName.from(name);
        } catch (InvalidUserNameException exception) {
            throw new InvalidOAuthUserInfoException();
        }
    }
}
