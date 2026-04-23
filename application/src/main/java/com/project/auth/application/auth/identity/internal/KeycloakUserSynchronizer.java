package com.project.auth.application.auth.identity.internal;

import com.project.auth.application.auth.exception.KeycloakAccountConflictException;
import com.project.auth.application.auth.identity.KeycloakUserClaims;
import com.project.auth.application.auth.identity.SyncKeycloakUserUseCase;
import com.project.auth.application.auth.identity.SyncedKeycloakUser;
import com.project.auth.application.auth.identity.port.out.LoadKeycloakUserPort;
import com.project.auth.application.auth.identity.port.out.RegisterKeycloakUserPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.application.support.audit.AuthAuditFailureReason;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class KeycloakUserSynchronizer implements SyncKeycloakUserUseCase {

    private final LoadKeycloakUserPort loadKeycloakUserPort;
    private final RegisterKeycloakUserPort registerKeycloakUserPort;
    private final Clock clock;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public KeycloakUserSynchronizer(
            LoadKeycloakUserPort loadKeycloakUserPort,
            RegisterKeycloakUserPort registerKeycloakUserPort,
            Clock clock,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        this.loadKeycloakUserPort = Objects.requireNonNull(loadKeycloakUserPort, "loadKeycloakUserPort must not be null");
        this.registerKeycloakUserPort = Objects.requireNonNull(
                registerKeycloakUserPort,
                "registerKeycloakUserPort must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
    }

    @Override
    @Transactional
    public SyncedKeycloakUser sync(KeycloakUserClaims claims) {
        ValidatedKeycloakUserClaims validatedClaims = KeycloakUserClaimsValidator.validate(claims);

        User user = loadKeycloakUserPort.findByProviderAndProviderSubject(AuthProvider.KEYCLOAK, validatedClaims.subject())
                .orElseGet(() -> register(validatedClaims));

        return SyncedKeycloakUser.from(user);
    }

    private User register(ValidatedKeycloakUserClaims claims) {
        if (loadKeycloakUserPort.existsByEmail(claims.email().value())) {
            publishConflict(claims, AuthAuditFailureReason.EMAIL_CONFLICT);
            throw new KeycloakAccountConflictException();
        }

        User user = User.registerKeycloak(
                UUID.randomUUID(),
                claims.email(),
                claims.name(),
                claims.subject(),
                Instant.now(clock)
        );

        try {
            User savedUser = registerKeycloakUserPort.save(user);
            authAuditEventPublisher.publish(AuthAuditEvent.info(
                    AuthAuditEventType.KEYCLOAK_USER_SYNC_SUCCESS.code(),
                    AuthAuditFields.USER_ID_HASH, AuthAuditFields.userIdHash(savedUser.getId()),
                    AuthAuditFields.EMAIL_MASKED, AuthAuditFields.maskedEmail(claims.email()),
                    AuthAuditFields.PROVIDER, AuthProvider.KEYCLOAK,
                    AuthAuditFields.SUBJECT, claims.subject()
            ));
            return savedUser;
        } catch (KeycloakAccountConflictException exception) {
            publishConflict(claims, AuthAuditFailureReason.DUPLICATE_EMAIL_RACE_CONDITION);
            throw exception;
        }
    }

    private void publishConflict(ValidatedKeycloakUserClaims claims, AuthAuditFailureReason reason) {
        authAuditEventPublisher.publish(AuthAuditEvent.warn(
                AuthAuditEventType.KEYCLOAK_USER_SYNC_CONFLICT.code(),
                AuthAuditFields.EMAIL_MASKED, AuthAuditFields.maskedEmail(claims.email()),
                AuthAuditFields.PROVIDER, AuthProvider.KEYCLOAK,
                AuthAuditFields.SUBJECT, claims.subject(),
                AuthAuditFields.REASON, reason.code()
        ));
    }
}
