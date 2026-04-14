package com.project.auth.application.user.signup;

import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.domain.user.model.User;

import java.util.Objects;

public class SignUpService implements SignUpUseCase {

    private final SignUpRegistrationService signUpRegistrationService;

    public SignUpService(SignUpRegistrationService signUpRegistrationService) {
        this.signUpRegistrationService = Objects.requireNonNull(
                signUpRegistrationService,
                "signUpRegistrationService must not be null"
        );
    }

    @Override
    public SignUpResult signUp(SignUpCommand command) {
        ValidatedSignUpCommand validatedCommand = SignUpCommandValidator.validate(command);
        User savedUser = signUpRegistrationService.register(validatedCommand);
        return SignUpResult.from(savedUser);
    }
}
