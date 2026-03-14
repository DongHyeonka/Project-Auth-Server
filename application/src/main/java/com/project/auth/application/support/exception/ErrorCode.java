package com.project.auth.application.support.exception;

public interface ErrorCode {

    int status();

    String code();

    String message();
}
