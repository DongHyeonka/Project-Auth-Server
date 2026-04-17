package com.project.auth.application.user.exception;

import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.application.support.exception.UserErrorCode;

public class DuplicateUserEmailException extends BusinessException {

    public DuplicateUserEmailException() {
        super(UserErrorCode.USER_EMAIL_ALREADY_EXISTS);
    }
}
