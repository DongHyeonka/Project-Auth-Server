package com.project.auth.application.auth.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;

public class InvalidOAuthUserInfoException extends BusinessException {

    public InvalidOAuthUserInfoException() {
        super(AuthErrorCode.OAUTH_USER_INFO_INVALID);
    }
}
