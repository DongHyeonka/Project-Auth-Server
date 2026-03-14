package com.project.auth.application.support.exception;

public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT(400, "COMMON-001", "요청 값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(500, "COMMON-999", "예상하지 못한 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;

    CommonErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public int status() {
        return status;
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
