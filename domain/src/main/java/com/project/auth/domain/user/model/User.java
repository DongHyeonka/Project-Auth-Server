package com.project.auth.domain.user.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final UserEmail email;
    private final UserName name;
    private final AuthProvider provider;
    private final ProviderSubject providerSubject;
    private final Instant createdAt;

    private User(
            UUID id,
            UserEmail email,
            UserName name,
            AuthProvider provider,
            ProviderSubject providerSubject,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.providerSubject = Objects.requireNonNull(providerSubject, "providerSubject must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static User registerKeycloak(
            UUID id,
            UserEmail email,
            UserName name,
            String providerSubject,
            Instant createdAt
    ) {
        return new User(id, email, name, AuthProvider.KEYCLOAK, ProviderSubject.from(providerSubject), createdAt);
    }

    public static User restore(
            UUID id,
            UserEmail email,
            UserName name,
            AuthProvider provider,
            String providerSubject,
            Instant createdAt
    ) {
        return new User(id, email, name, provider, ProviderSubject.from(providerSubject), createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email.value();
    }

    public String getName() {
        return name.value();
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject.value();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
