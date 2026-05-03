package com.project.auth.application.support.exception;

public abstract class BusinessException extends RuntimeException {

    private final ClientFacingErrorCode errorCode;

    protected BusinessException(ClientFacingErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    protected BusinessException(ClientFacingErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    protected BusinessException(ClientFacingErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    protected BusinessException(ClientFacingErrorCode errorCode, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        this.errorCode = errorCode;
    }

    public ClientFacingErrorCode getErrorCode() {
        return errorCode;
    }
}
