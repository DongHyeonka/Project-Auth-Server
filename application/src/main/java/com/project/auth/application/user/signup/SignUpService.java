package com.project.auth.application.user.signup;

import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.application.user.signup.port.out.PasswordHasherPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.domain.user.model.User;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SignUpService implements SignUpUseCase {

    private final RegisterUserPort registerUserPort;
    private final PasswordHasherPort passwordHasherPort;
    private final Clock clock;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public SignUpService(
            RegisterUserPort registerUserPort,
            PasswordHasherPort passwordHasherPort,
            Clock clock,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        this.registerUserPort = Objects.requireNonNull(registerUserPort, "registerUserPort must not be null");
        this.passwordHasherPort = Objects.requireNonNull(passwordHasherPort, "passwordHasherPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
    }

    @Override
    public SignUpResult signUp(SignUpCommand command) {
        ValidatedSignUpCommand validatedCommand = SignUpCommandValidator.validate(command);

        if (registerUserPort.existsByEmail(validatedCommand.email())) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    "SIGNUP_FAILURE",
                    "email", validatedCommand.email(),
                    "reason", "duplicate_email"
            ));
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
        authAuditEventPublisher.publish(AuthAuditEvent.info(
                "SIGNUP_SUCCESS",
                "userId", savedUser.getId(),
                "email", savedUser.getEmail()
        ));
        return SignUpResult.from(savedUser);
    }
}
