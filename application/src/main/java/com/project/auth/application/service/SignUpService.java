package com.project.auth.application.service;

import com.project.auth.application.dto.SignUpCommand;
import com.project.auth.application.dto.SignUpResult;
import com.project.auth.application.exception.DuplicateUserEmailException;
import com.project.auth.application.exception.InvalidUserSignUpException;
import com.project.auth.application.exception.UserErrorCode;
import com.project.auth.application.port.out.PasswordEncoderPort;
import com.project.auth.application.port.out.UserRepositoryPort;
import com.project.auth.application.usecase.SignUpUseCase;
import com.project.auth.domain.exception.InvalidUserEmailException;
import com.project.auth.domain.exception.InvalidUserNameException;
import com.project.auth.domain.exception.InvalidUserPasswordException;
import com.project.auth.domain.model.User;
import com.project.auth.domain.model.UserEmail;
import com.project.auth.domain.model.UserName;
import com.project.auth.domain.service.UserPasswordPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SignUpService implements SignUpUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final Clock clock;

    public SignUpService(
            UserRepositoryPort userRepositoryPort,
            PasswordEncoderPort passwordEncoderPort,
            Clock clock
    ) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort must not be null");
        this.passwordEncoderPort = Objects.requireNonNull(passwordEncoderPort, "passwordEncoderPort must not be null");
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

        if (userRepositoryPort.existsByEmail(userEmail)) {
            throw new DuplicateUserEmailException();
        }

        User user;
        try {
            user = User.registerLocal(
                    UUID.randomUUID(),
                    userEmail,
                    passwordEncoderPort.encode(command.password()),
                    userName,
                    Instant.now(clock)
            );
        } catch (InvalidUserPasswordException exception) {
            throw new InvalidUserSignUpException(UserErrorCode.USER_PASSWORD_INVALID, exception.getMessage());
        }

        User savedUser = userRepositoryPort.save(user);

        return new SignUpResult(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getProvider().name(),
                savedUser.getCreatedAt()
        );
    }
}
