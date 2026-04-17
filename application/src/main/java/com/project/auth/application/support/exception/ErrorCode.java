package com.project.auth.application.support.exception;

public sealed interface ErrorCode
        permits CommonErrorCode, AuthErrorCode, UserErrorCode, ExternalErrorCode {

    String code();

    String message();
}
