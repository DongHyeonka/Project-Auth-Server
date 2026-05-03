package com.project.auth.application.support.exception;

public enum AuthErrorCode implements ClientFacingErrorCode {
    AUTHENTICATION_REQUIRED("AUTH-001", "인증이 필요합니다."),
    ACCESS_DENIED("AUTH-002", "접근 권한이 없습니다."),
    KEYCLOAK_CLAIMS_INVALID("AUTH-003", "Keycloak 토큰 클레임이 올바르지 않습니다."),
    KEYCLOAK_USER_NOT_FOUND("AUTH-004", "연결된 내부 Keycloak 사용자를 찾을 수 없습니다.");

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
