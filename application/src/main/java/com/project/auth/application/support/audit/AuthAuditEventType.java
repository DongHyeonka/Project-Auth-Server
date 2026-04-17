package com.project.auth.application.support.audit;

public enum AuthAuditEventType {
    KEYCLOAK_USER_SYNC_SUCCESS("KEYCLOAK_USER_SYNC_SUCCESS"),
    KEYCLOAK_USER_SYNC_CONFLICT("KEYCLOAK_USER_SYNC_CONFLICT"),
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
