package com.project.auth.application.user.exception;

import com.project.auth.application.support.exception.BusinessException;

public class DuplicateUserEmailException extends BusinessException {

    public DuplicateUserEmailException() {
        super(UserErrorCode.USER_EMAIL_ALREADY_EXISTS);
    }
}
