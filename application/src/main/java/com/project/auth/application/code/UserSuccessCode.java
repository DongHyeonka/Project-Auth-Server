package com.project.auth.application.code;

public enum UserSuccessCode implements SuccessCode {
    USER_SIGNED_UP("USER_SIGNED_UP", "회원가입이 완료되었습니다.");

    private final String code;
    private final String message;

    UserSuccessCode(String code, String message) {
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
