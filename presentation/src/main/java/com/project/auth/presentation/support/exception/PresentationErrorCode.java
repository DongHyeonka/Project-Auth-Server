package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.ClientFacingErrorCode;

public enum PresentationErrorCode implements ClientFacingErrorCode {
    INVALID_INPUT("PRES-001", "요청 값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY("PRES-002", "요청 본문을 읽을 수 없습니다."),
    METHOD_NOT_ALLOWED("PRES-003", "지원하지 않는 HTTP 메서드입니다."),
    MISSING_PARAMETER("PRES-004", "필수 요청 파라미터가 누락되었습니다."),
    RESOURCE_NOT_FOUND("PRES-005", "요청한 리소스를 찾을 수 없습니다."),
    CONSTRAINT_VIOLATION("PRES-006", "요청 값이 제약 조건을 위반했습니다."),
    UNSUPPORTED_MEDIA_TYPE("PRES-007", "지원하지 않는 Content-Type입니다."),
    NOT_ACCEPTABLE("PRES-008", "요청한 응답 형식을 제공할 수 없습니다."),
    MISSING_HEADER("PRES-009", "필수 요청 헤더가 누락되었습니다."),
    REQUEST_BINDING_FAILED("PRES-010", "요청 바인딩에 실패했습니다."),
    MESSAGE_NOT_WRITABLE("PRES-011", "응답 데이터를 처리할 수 없습니다."),
    PAYLOAD_TOO_LARGE("PRES-012", "요청 본문 크기가 허용 한도를 초과했습니다."),
    UNHANDLED_CLIENT_ERROR("PRES-013", "처리하지 못한 클라이언트 오류입니다."),
    TYPE_MISMATCH("PRES-014", "요청 파라미터 타입이 올바르지 않습니다."),
    INVALID_PARAMETER("PRES-015", "요청 파라미터 검증에 실패했습니다.");

    private final String code;
    private final String message;

    PresentationErrorCode(String code, String message) {
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
