package com.project.auth.infrastructure.support.exception;

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
