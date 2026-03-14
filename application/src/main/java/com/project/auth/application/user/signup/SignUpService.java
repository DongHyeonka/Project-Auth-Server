package com.project.auth.application.user.signup;

import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.application.user.exception.InvalidUserSignUpException;
import com.project.auth.application.user.exception.UserErrorCode;
import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.application.user.signup.port.out.PasswordHasherPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.domain.user.exception.InvalidUserEmailException;
import com.project.auth.domain.user.exception.InvalidUserNameException;
import com.project.auth.domain.user.exception.InvalidUserPasswordException;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import com.project.auth.domain.user.service.UserPasswordPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SignUpService implements SignUpUseCase {

    private final RegisterUserPort registerUserPort;
    private final PasswordHasherPort passwordHasherPort;
    private final Clock clock;

    public SignUpService(
            RegisterUserPort registerUserPort,
            PasswordHasherPort passwordHasherPort,
            Clock clock
    ) {
        this.registerUserPort = Objects.requireNonNull(registerUserPort, "registerUserPort must not be null");
        this.passwordHasherPort = Objects.requireNonNull(passwordHasherPort, "passwordHasherPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public SignUpResult signUp(SignUpCommand command) {
        UserEmail userEmail;
        UserName userName;
        try {
            userEmail = UserEmail.from(command.email());
            userName = UserName.from(command.name());
            UserPasswordPolicy.validateRaw(command.password());
        } catch (InvalidUserEmailException exception) {
            throw new InvalidUserSignUpException(UserErrorCode.USER_EMAIL_INVALID, exception.getMessage());
        } catch (InvalidUserNameException exception) {
            throw new InvalidUserSignUpException(UserErrorCode.USER_NAME_INVALID, exception.getMessage());
        } catch (InvalidUserPasswordException exception) {
            throw new InvalidUserSignUpException(UserErrorCode.USER_PASSWORD_INVALID, exception.getMessage());
        }

        if (registerUserPort.existsByEmail(userEmail)) {
            throw new DuplicateUserEmailException();
        }

        User user;
        try {
            user = User.registerLocal(
                    UUID.randomUUID(),
                    userEmail,
                    passwordHasherPort.encode(command.password()),
                    userName,
                    Instant.now(clock)
            );
        } catch (InvalidUserPasswordException exception) {
            throw new InvalidUserSignUpException(UserErrorCode.USER_PASSWORD_INVALID, exception.getMessage());
        }

        User savedUser = registerUserPort.save(user);

        return new SignUpResult(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getProvider().name(),
                savedUser.getCreatedAt()
        );
    }
}
