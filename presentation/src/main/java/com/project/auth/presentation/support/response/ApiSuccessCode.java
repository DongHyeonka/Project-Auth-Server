package com.project.auth.presentation.support.response;

public enum ApiSuccessCode {
    AUTHENTICATED_USER_LOADED("AUTHENTICATED_USER_LOADED", "현재 사용자 조회가 완료되었습니다.");

    private final String code;
    private final String message;

    ApiSuccessCode(String code, String message) {
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
