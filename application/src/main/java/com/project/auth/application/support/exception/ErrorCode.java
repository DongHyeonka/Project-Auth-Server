package com.project.auth.application.support.exception;

public sealed interface ErrorCode
        permits ClientFacingErrorCode, ExternalErrorCode {

    String code();

    String message();
}
