package com.project.auth.domain.exception;

public class InvalidUserEmailException extends DomainException {

    public InvalidUserEmailException() {
        super("유효한 이메일 형식이 아닙니다.");
    }
}
