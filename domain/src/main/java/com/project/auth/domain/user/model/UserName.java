package com.project.auth.domain.user.model;

import com.project.auth.domain.user.exception.InvalidUserNameException;

public record UserName(String value) {

    public UserName {
        if (value == null || value.isBlank()) {
            throw new InvalidUserNameException();
        }

        String normalizedName = value.trim();
        if (normalizedName.length() < 2 || normalizedName.length() > 20) {
            throw new InvalidUserNameException();
        }

        value = normalizedName;
    }

    public static UserName from(String rawName) {
        return new UserName(rawName);
    }
}
