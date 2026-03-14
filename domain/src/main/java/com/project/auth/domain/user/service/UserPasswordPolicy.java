package com.project.auth.domain.user.service;

import com.project.auth.domain.user.exception.InvalidUserPasswordException;

public final class UserPasswordPolicy {

    private UserPasswordPolicy() {
    }

    public static void validateRaw(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank() || rawPassword.length() < 8 || rawPassword.length() > 50) {
            throw new InvalidUserPasswordException();
        }
    }

    public static void validateEncoded(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new InvalidUserPasswordException();
        }
    }
}
