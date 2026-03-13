package com.project.auth.domain.model;

import com.project.auth.domain.exception.InvalidUserEmailException;

import java.util.Locale;
import java.util.regex.Pattern;

public record UserEmail(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    public UserEmail {
        if (value == null || value.isBlank()) {
            throw new InvalidUserEmailException();
        }
    }

    public static UserEmail from(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new InvalidUserEmailException();
        }

        String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new InvalidUserEmailException();
        }

        return new UserEmail(normalizedEmail);
    }
}
