package com.project.auth.application.auth.exception;

import com.project.auth.application.support.exception.ErrorCode;

public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("AUTH-001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    OAUTH_USER_INFO_INVALID("AUTH-002", "소셜 로그인 사용자 정보가 올바르지 않습니다."),
    OAUTH_ACCOUNT_CONFLICT("AUTH-003", "동일한 이메일의 기존 계정이 있어 소셜 로그인을 진행할 수 없습니다."),
    OAUTH_LOGIN_FAILED("AUTH-004", "소셜 로그인에 실패했습니다."),
    UNSUPPORTED_OAUTH_PROVIDER("AUTH-005", "지원하지 않는 OAuth2 공급자입니다.");

    private final String code;
    private final String message;

    AuthErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
