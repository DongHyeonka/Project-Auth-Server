package com.project.auth.application.support.audit;

public enum AuthAuditEventType {
    SIGNUP_FAILURE("SIGNUP_FAILURE"),
    SIGNUP_SUCCESS("SIGNUP_SUCCESS"),
    LOGIN_FAILURE("LOGIN_FAILURE"),
    LOGIN_SUCCESS("LOGIN_SUCCESS"),
    OAUTH_LOGIN_FAILURE("OAUTH_LOGIN_FAILURE"),
    OAUTH_LOGIN_SUCCESS("OAUTH_LOGIN_SUCCESS");

    private final String code;

    AuthAuditEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
