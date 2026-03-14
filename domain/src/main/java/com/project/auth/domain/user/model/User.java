package com.project.auth.domain.user.model;

import com.project.auth.domain.user.service.UserPasswordPolicy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final UserEmail email;
    private final String encodedPassword;
    private final UserName name;
    private final AuthProvider provider;
    private final String providerSubject;
    private final Instant createdAt;

    private User(
            UUID id,
            UserEmail email,
            String encodedPassword,
            UserName name,
            AuthProvider provider,
            String providerSubject,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.encodedPassword = encodedPassword;
        this.providerSubject = providerSubject;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static User registerLocal(
            UUID id,
            UserEmail email,
            String encodedPassword,
            UserName name,
            Instant createdAt
    ) {
        UserPasswordPolicy.validateEncoded(encodedPassword);

        return new User(id, email, encodedPassword, name, AuthProvider.LOCAL, null, createdAt);
    }

    public static User registerSocial(
            UUID id,
            UserEmail email,
            UserName name,
            AuthProvider provider,
            String providerSubject,
            Instant createdAt
    ) {
        if (provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException("Social registration cannot use LOCAL provider.");
        }

        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("providerSubject must not be blank.");
        }

        return new User(id, email, null, name, provider, providerSubject.trim(), createdAt);
    }

    public static User restore(
            UUID id,
            UserEmail email,
            String encodedPassword,
            UserName name,
            AuthProvider provider,
            String providerSubject,
            Instant createdAt
    ) {
        if (provider == AuthProvider.LOCAL) {
            UserPasswordPolicy.validateEncoded(encodedPassword);
            providerSubject = null;
        } else if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("providerSubject must not be blank.");
        }

        return new User(id, email, encodedPassword, name, provider, providerSubject, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email.value();
    }

    public String getEncodedPassword() {
        return encodedPassword;
    }

    public String getName() {
        return name.value();
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
