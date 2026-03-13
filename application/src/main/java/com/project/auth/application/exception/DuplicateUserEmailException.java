package com.project.auth.application.exception;

public class DuplicateUserEmailException extends BusinessException {

    public DuplicateUserEmailException() {
        super(UserErrorCode.USER_EMAIL_ALREADY_EXISTS);
    }
}
