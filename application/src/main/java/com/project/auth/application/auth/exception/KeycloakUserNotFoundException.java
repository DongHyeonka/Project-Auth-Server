package com.project.auth.application.auth.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;

public class KeycloakUserNotFoundException extends BusinessException {

    public KeycloakUserNotFoundException() {
        super(AuthErrorCode.KEYCLOAK_USER_NOT_FOUND);
    }
}
