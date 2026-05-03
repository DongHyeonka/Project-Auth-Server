package com.project.auth.infrastructure.support.exception;

/**
 * Internal-only exception representing an infrastructure-layer failure (DB, external
 * HTTP client, secret store, etc.). The configured @ExceptionHandler logs the full
 * stack trace at ERROR including the {@code detailMessage}; therefore adapters MUST NOT
 * include sensitive material in {@code detailMessage}.
 *
 * Forbidden in detailMessage: connection strings, DB credentials, vault tokens, JWT
 * payload contents, raw user input, full request/response bodies of external calls.
 *
 * Allowed in detailMessage: opaque correlation ids, host/service names, sanitized
 * status codes, and plain-language descriptions of the failure mode. The detail is
 * for operator triage, not user diagnosis — clients always see COMMON-999.
 */
public class InfrastructureException extends RuntimeException {

    private final InfrastructureErrorCode errorCode;

    public InfrastructureException(InfrastructureErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public InfrastructureException(InfrastructureErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public InfrastructureException(InfrastructureErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public InfrastructureException(InfrastructureErrorCode errorCode, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        this.errorCode = errorCode;
    }

    public InfrastructureErrorCode getErrorCode() {
        return errorCode;
    }
}
