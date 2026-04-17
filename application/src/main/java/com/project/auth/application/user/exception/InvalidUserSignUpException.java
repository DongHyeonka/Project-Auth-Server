package com.project.auth.application.user.exception;

import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.application.support.exception.UserErrorCode;

public class InvalidUserSignUpException extends BusinessException {

    public InvalidUserSignUpException(UserErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
