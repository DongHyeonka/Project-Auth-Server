package com.project.auth.domain.user.exception;

/**
 * Base domain exception for invariant violations.
 * Messages on this type are currently exposed to clients by the fallback web handler,
 * so they must stay safe, localized, and user-facing.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
