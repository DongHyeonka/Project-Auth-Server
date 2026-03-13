package com.project.auth.domain.model;

import com.project.auth.domain.service.UserPasswordPolicy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final UserEmail email;
    private final String encodedPassword;
    private final UserName name;
    private final AuthProvider provider;
    private final Instant createdAt;

    private User(
            UUID id,
            UserEmail email,
            String encodedPassword,
            UserName name,
            AuthProvider provider,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.encodedPassword = Objects.requireNonNull(encodedPassword, "encodedPassword must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
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

        return new User(id, email, encodedPassword, name, AuthProvider.LOCAL, createdAt);
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
