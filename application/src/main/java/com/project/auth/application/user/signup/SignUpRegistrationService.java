package com.project.auth.application.user.signup;

import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFailureReason;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.application.user.signup.port.out.PasswordHasherPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.domain.user.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SignUpRegistrationService {

    private final RegisterUserPort registerUserPort;
    private final PasswordHasherPort passwordHasherPort;
    private final Clock clock;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public SignUpRegistrationService(
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

    @Transactional
    public User register(ValidatedSignUpCommand command) {
        if (registerUserPort.existsByEmail(command.email())) {
            authAuditEventPublisher.publish(AuthAuditEvent.warn(
                    AuthAuditEventType.SIGNUP_FAILURE.code(),
                    "emailMasked", AuthAuditFields.maskedEmail(command.email()),
                    "reason", AuthAuditFailureReason.DUPLICATE_EMAIL.code()
            ));
            throw new DuplicateUserEmailException();
        }

        User user = User.registerLocal(
                UUID.randomUUID(),
                command.email(),
                passwordHasherPort.encode(command.rawPassword()),
                command.name(),
                Instant.now(clock)
        );

        User savedUser = registerUserPort.save(user);
        authAuditEventPublisher.publish(AuthAuditEvent.info(
                AuthAuditEventType.SIGNUP_SUCCESS.code(),
                "userIdHash", AuthAuditFields.userIdHash(savedUser.getId()),
                "emailMasked", AuthAuditFields.maskedEmail(command.email())
        ));
        return savedUser;
    }
}
