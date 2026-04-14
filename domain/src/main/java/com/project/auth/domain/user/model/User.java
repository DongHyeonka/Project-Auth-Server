package com.project.auth.domain.user.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final UserEmail email;
    private final EncodedPassword encodedPassword;
    private final UserName name;
    private final AuthProvider provider;
    private final ProviderSubject providerSubject;
    private final Instant createdAt;

    private User(
            UUID id,
            UserEmail email,
            EncodedPassword encodedPassword,
            UserName name,
            AuthProvider provider,
            ProviderSubject providerSubject,
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
        return new User(id, email, EncodedPassword.from(encodedPassword), name, AuthProvider.LOCAL, null, createdAt);
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

        return new User(id, email, null, name, provider, ProviderSubject.from(providerSubject), createdAt);
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
            return new User(id, email, EncodedPassword.from(encodedPassword), name, provider, null, createdAt);
        }

        return new User(id, email, null, name, provider, ProviderSubject.from(providerSubject), createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email.value();
    }

    public String getEncodedPassword() {
        return encodedPassword == null ? null : encodedPassword.value();
    }

    public String getName() {
        return name.value();
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject == null ? null : providerSubject.value();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
