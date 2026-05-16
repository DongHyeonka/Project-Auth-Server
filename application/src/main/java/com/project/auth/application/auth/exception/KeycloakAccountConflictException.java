package com.project.auth.application.auth.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;

public class KeycloakAccountConflictException extends BusinessException {

    public KeycloakAccountConflictException() {
        super(AuthErrorCode.KEYCLOAK_ACCOUNT_CONFLICT);
    }
}
