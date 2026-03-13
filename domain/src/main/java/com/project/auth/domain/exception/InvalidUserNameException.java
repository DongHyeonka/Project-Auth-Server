package com.project.auth.domain.exception;

public class InvalidUserNameException extends DomainException {

    public InvalidUserNameException() {
        super("이름은 2자 이상 20자 이하여야 합니다.");
    }
}
