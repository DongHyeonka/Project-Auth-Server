package com.project.auth.application.support.exception;

public sealed interface ErrorCode
        permits CommonErrorCode, AuthErrorCode, ExternalErrorCode {

    String code();

    String message();
}
