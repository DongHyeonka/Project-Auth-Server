package com.project.auth.application.auth.identity.internal;

import com.project.auth.application.auth.exception.KeycloakUserNotFoundException;
import com.project.auth.application.auth.identity.KeycloakUserClaims;
import com.project.auth.application.auth.identity.LoadKeycloakUserUseCase;
import com.project.auth.application.auth.identity.LoadedKeycloakUser;
import com.project.auth.application.auth.identity.port.out.LoadKeycloakUserPort;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;

import java.util.Objects;

public class KeycloakUserLoader implements LoadKeycloakUserUseCase {

    private final LoadKeycloakUserPort loadKeycloakUserPort;
    private final AuthAuditEventPublisher authAuditEventPublisher;

    public KeycloakUserLoader(
            LoadKeycloakUserPort loadKeycloakUserPort,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        this.loadKeycloakUserPort = Objects.requireNonNull(loadKeycloakUserPort, "loadKeycloakUserPort must not be null");
        this.authAuditEventPublisher = Objects.requireNonNull(
                authAuditEventPublisher,
                "authAuditEventPublisher must not be null"
        );
    }

    @Override
    public LoadedKeycloakUser load(KeycloakUserClaims claims) {
        ValidatedKeycloakUserClaims validatedClaims = KeycloakUserClaimsValidator.validate(claims);

        User user = loadKeycloakUserPort.findByProviderAndProviderSubject(AuthProvider.KEYCLOAK, validatedClaims.subject())
                .orElseThrow(() -> notFound(validatedClaims.subject()));

        return LoadedKeycloakUser.from(user);
    }

    private KeycloakUserNotFoundException notFound(String subject) {
        authAuditEventPublisher.publish(AuthAuditEvent.warn(
                AuthAuditEventType.KEYCLOAK_USER_NOT_FOUND.code(),
                AuthAuditFields.PROVIDER, AuthProvider.KEYCLOAK,
                AuthAuditFields.SUBJECT, subject
        ));
        return new KeycloakUserNotFoundException();
    }
}
