package com.project.auth.application.support.code;

public enum SuccessCode {
    USER_SIGNED_UP("USER_SIGNED_UP", "회원가입이 완료되었습니다."),
    AUTH_LOGIN_SUCCEEDED("AUTH_LOGIN_SUCCEEDED", "로그인이 완료되었습니다.");

    private final String code;
    private final String message;

    SuccessCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
