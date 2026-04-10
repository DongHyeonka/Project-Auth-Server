package com.project.auth.application.user.signup;

import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.application.user.signup.port.out.PasswordHasherPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.domain.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SignUpService implements SignUpUseCase {

    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

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
        ValidatedSignUpCommand validatedCommand = SignUpCommandValidator.validate(command);

        if (registerUserPort.existsByEmail(validatedCommand.email())) {
            audit.warn("SIGNUP_FAILURE email={} reason=duplicate_email", validatedCommand.email());
            throw new DuplicateUserEmailException();
        }

        User user = User.registerLocal(
                UUID.randomUUID(),
                validatedCommand.email(),
                passwordHasherPort.encode(validatedCommand.rawPassword()),
                validatedCommand.name(),
                Instant.now(clock)
        );

        User savedUser = registerUserPort.save(user);
        audit.info("SIGNUP_SUCCESS userId={} email={}", savedUser.getId(), savedUser.getEmail());
        return SignUpResult.from(savedUser);
    }
}
