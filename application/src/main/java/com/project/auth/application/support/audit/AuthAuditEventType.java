package com.project.auth.application.support.audit;

public enum AuthAuditEventType {
    SIGNUP_FAILURE("SIGNUP_FAILURE"),
    SIGNUP_SUCCESS("SIGNUP_SUCCESS"),
    LOGIN_FAILURE("LOGIN_FAILURE"),
    LOGIN_SUCCESS("LOGIN_SUCCESS"),
    OAUTH_LOGIN_FAILURE("OAUTH_LOGIN_FAILURE"),
    OAUTH_LOGIN_SUCCESS("OAUTH_LOGIN_SUCCESS"),
    OAUTH_AUTHENTICATION_SUCCESS("OAUTH_AUTHENTICATION_SUCCESS"),
    OAUTH_AUTHENTICATION_FAILURE("OAUTH_AUTHENTICATION_FAILURE"),
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED"),
    ACCESS_DENIED("ACCESS_DENIED"),
    TOKEN_ISSUED("TOKEN_ISSUED");

    private final String code;

    AuthAuditEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
