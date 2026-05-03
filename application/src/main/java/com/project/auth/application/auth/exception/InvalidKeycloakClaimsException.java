package com.project.auth.application.auth.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;

public class InvalidKeycloakClaimsException extends BusinessException {

    public InvalidKeycloakClaimsException() {
        super(AuthErrorCode.KEYCLOAK_CLAIMS_INVALID);
    }
}
