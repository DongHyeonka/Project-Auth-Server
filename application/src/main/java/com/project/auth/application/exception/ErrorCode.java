package com.project.auth.application.exception;

public interface ErrorCode {

    int status();

    String code();

    String message();
}
