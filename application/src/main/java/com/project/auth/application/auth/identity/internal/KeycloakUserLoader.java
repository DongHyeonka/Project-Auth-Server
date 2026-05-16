package com.project.auth.application.auth.identity.internal;

import com.project.auth.application.auth.exception.KeycloakAccountConflictException;
import com.project.auth.application.auth.identity.KeycloakUserClaims;
import com.project.auth.application.auth.identity.LoadKeycloakUserUseCase;
import com.project.auth.application.auth.identity.LoadedKeycloakUser;
import com.project.auth.application.auth.identity.port.out.LoadKeycloakUserPort;
import com.project.auth.application.auth.identity.port.out.RegisterKeycloakUserPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class KeycloakUserLoader implements LoadKeycloakUserUseCase {

    private final LoadKeycloakUserPort loadKeycloakUserPort;
    private final RegisterKeycloakUserPort registerKeycloakUserPort;
    private final AuthAuditEventPublisher authAuditEventPublisher;
    private final Clock clock;

    public KeycloakUserLoader(
            LoadKeycloakUserPort loadKeycloakUserPort,
            RegisterKeycloakUserPort registerKeycloakUserPort,
            AuthAuditEventPublisher authAuditEventPublisher,
            Clock clock
    ) {
        this.loadKeycloakUserPort = Objects.requireNonNull(loadKeycloakUserPort, "loadKeycloakUserPort must not be null");
        this.registerKeycloakUserPort = Objects.requireNonNull(
                registerKeycloakUserPort,
                "registerKeycloakUserPort must not be null"
        );
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public LoadedKeycloakUser load(KeycloakUserClaims claims) {
        ValidatedKeycloakUserClaims validatedClaims = KeycloakUserClaimsValidator.validate(claims);

        User user = loadKeycloakUserPort.findByProviderAndProviderSubject(AuthProvider.KEYCLOAK, validatedClaims.subject())
                .orElseGet(() -> autoRegister(validatedClaims));

        return LoadedKeycloakUser.from(user);
    }

    private User autoRegister(ValidatedKeycloakUserClaims claims) {
        if (loadKeycloakUserPort.existsByEmail(claims.email())) {
            throw new KeycloakAccountConflictException();
        }

        User newUser = User.registerKeycloak(
                UUID.randomUUID(),
                claims.email(),
                claims.name(),
                claims.subject(),
                Instant.now(clock)
        );
        User saved = registerKeycloakUserPort.register(newUser);
        authAuditEventPublisher.publish(AuthAuditEvent.info(
                AuthAuditEventType.KEYCLOAK_USER_AUTO_REGISTERED.code(),
                AuthAuditFields.PROVIDER, AuthProvider.KEYCLOAK,
                AuthAuditFields.SUBJECT, claims.subject()
        ));
        return saved;
    }
}
