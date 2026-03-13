package com.project.auth.domain.exception;

public class InvalidUserPasswordException extends DomainException {

    public InvalidUserPasswordException() {
        super("비밀번호는 8자 이상 50자 이하여야 합니다.");
    }
}
