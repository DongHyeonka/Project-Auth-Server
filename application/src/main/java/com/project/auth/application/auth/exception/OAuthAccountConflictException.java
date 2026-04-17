package com.project.auth.application.auth.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;

public class OAuthAccountConflictException extends BusinessException {

    public OAuthAccountConflictException() {
        super(AuthErrorCode.OAUTH_ACCOUNT_CONFLICT);
    }
}
