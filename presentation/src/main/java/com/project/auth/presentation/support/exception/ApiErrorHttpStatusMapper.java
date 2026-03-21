package com.project.auth.presentation.support.exception;

import com.project.auth.application.auth.exception.AuthErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.exception.ErrorCode;
import com.project.auth.application.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;

public final class ApiErrorHttpStatusMapper {

    private ApiErrorHttpStatusMapper() {
    }

    public static HttpStatus map(ErrorCode errorCode) {
        return switch (errorCode) {
            case CommonErrorCode commonErrorCode -> mapCommon(commonErrorCode);
            case AuthErrorCode authErrorCode -> mapAuth(authErrorCode);
            case UserErrorCode userErrorCode -> mapUser(userErrorCode);
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static HttpStatus mapCommon(CommonErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static HttpStatus mapAuth(AuthErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_CREDENTIALS, OAUTH_LOGIN_FAILED -> HttpStatus.UNAUTHORIZED;
            case OAUTH_USER_INFO_INVALID, UNSUPPORTED_OAUTH_PROVIDER -> HttpStatus.BAD_REQUEST;
            case OAUTH_ACCOUNT_CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private static HttpStatus mapUser(UserErrorCode errorCode) {
        return switch (errorCode) {
            case USER_EMAIL_INVALID, USER_PASSWORD_INVALID, USER_NAME_INVALID -> HttpStatus.BAD_REQUEST;
            case USER_EMAIL_ALREADY_EXISTS -> HttpStatus.CONFLICT;
        };
    }
}
