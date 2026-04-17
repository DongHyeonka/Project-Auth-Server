package com.project.auth.domain.user.model;

public record ProviderSubject(String value) {

    public ProviderSubject {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("providerSubject must not be blank.");
        }
        value = value.trim();
    }

    public static ProviderSubject from(String value) {
        return new ProviderSubject(value);
    }
}
