package com.project.auth.application.user.exception;

import com.project.auth.application.support.exception.ErrorCode;

public enum UserErrorCode implements ErrorCode {
    USER_EMAIL_INVALID("USER-001", "유효한 이메일 형식이 아닙니다."),
    USER_PASSWORD_INVALID("USER-002", "비밀번호는 8자 이상 50자 이하여야 합니다."),
    USER_NAME_INVALID("USER-003", "이름은 2자 이상 20자 이하여야 합니다."),
    USER_EMAIL_ALREADY_EXISTS("USER-004", "이미 가입된 이메일입니다.");

    private final String code;
    private final String message;

    UserErrorCode(String code, String message) {
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
