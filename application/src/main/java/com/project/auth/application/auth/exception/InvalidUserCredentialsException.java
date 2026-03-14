package com.project.auth.application.auth.exception;

import com.project.auth.application.support.exception.BusinessException;

public class InvalidUserCredentialsException extends BusinessException {

    public InvalidUserCredentialsException() {
        super(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
