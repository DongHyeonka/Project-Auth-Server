package com.project.auth.application.exception;

public class InvalidUserSignUpException extends BusinessException {

    public InvalidUserSignUpException(UserErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
