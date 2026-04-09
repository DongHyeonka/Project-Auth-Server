package com.project.auth.application.support.exception;

public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT("COMMON-001", "요청 값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY("COMMON-002", "요청 본문을 읽을 수 없습니다."),
    METHOD_NOT_ALLOWED("COMMON-003", "지원하지 않는 HTTP 메서드입니다."),
    MISSING_PARAMETER("COMMON-004", "필수 요청 파라미터가 누락되었습니다."),
    DOMAIN_RULE_VIOLATION("COMMON-005", "요청 값이 도메인 규칙에 맞지 않습니다."),
    RESOURCE_NOT_FOUND("COMMON-006", "요청한 리소스를 찾을 수 없습니다."),
    CONSTRAINT_VIOLATION("COMMON-007", "요청 값이 제약 조건을 위반했습니다."),
    UNSUPPORTED_MEDIA_TYPE("COMMON-008", "지원하지 않는 Content-Type입니다."),
    NOT_ACCEPTABLE("COMMON-009", "요청한 응답 형식을 제공할 수 없습니다."),
    MISSING_HEADER("COMMON-010", "필수 요청 헤더가 누락되었습니다."),
    MESSAGE_NOT_WRITABLE("COMMON-011", "응답 데이터를 처리할 수 없습니다."),
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
