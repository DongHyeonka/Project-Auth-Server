package com.project.auth.application.support.audit;

public enum AuthAuditFailureReason {
    DUPLICATE_EMAIL("duplicate_email"),
    USER_NOT_FOUND("user_not_found"),
    NON_LOCAL_PROVIDER("non_local_provider"),
    INVALID_PASSWORD("invalid_password"),
    EMAIL_CONFLICT("email_conflict"),
    DUPLICATE_EMAIL_RACE_CONDITION("duplicate_email_race_condition");

    private final String code;

    AuthAuditFailureReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
