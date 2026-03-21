package com.project.auth.application.support.exception;

public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT("COMMON-001", "요청 값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR("COMMON-999", "예상하지 못한 오류가 발생했습니다.");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
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
