package com.project.auth.application.support.audit;

public enum AuthAuditFailureReason {
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
