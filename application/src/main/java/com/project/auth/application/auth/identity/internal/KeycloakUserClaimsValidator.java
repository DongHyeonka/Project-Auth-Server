package com.project.auth.application.auth.identity.internal;

import com.project.auth.application.auth.exception.InvalidKeycloakClaimsException;
import com.project.auth.application.auth.identity.KeycloakUserClaims;
import com.project.auth.domain.user.exception.DomainException;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;

final class KeycloakUserClaimsValidator {

    private KeycloakUserClaimsValidator() {
    }

    static ValidatedKeycloakUserClaims validate(KeycloakUserClaims claims) {
        if (claims == null
                || isBlank(claims.subject())
                || isBlank(claims.email())
                || isBlank(claims.name())) {
            throw new InvalidKeycloakClaimsException();
        }

        try {
            return new ValidatedKeycloakUserClaims(
                    claims.subject().trim(),
                    UserEmail.from(claims.email()),
                    UserName.from(claims.name())
            );
        } catch (DomainException exception) {
            throw new InvalidKeycloakClaimsException();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
