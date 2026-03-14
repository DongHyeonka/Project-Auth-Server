package com.project.auth.application.auth.exception;

import com.project.auth.application.support.exception.BusinessException;

public class UnsupportedOAuthProviderException extends BusinessException {

    public UnsupportedOAuthProviderException() {
        super(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }
}
