package com.project.auth.application.support.audit;

public enum AuthAuditEventType {
    KEYCLOAK_USER_NOT_FOUND("KEYCLOAK_USER_NOT_FOUND"),
    KEYCLOAK_USER_AUTO_REGISTERED("KEYCLOAK_USER_AUTO_REGISTERED"),
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED"),
    ACCESS_DENIED("ACCESS_DENIED");

    private final String code;

    AuthAuditEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
