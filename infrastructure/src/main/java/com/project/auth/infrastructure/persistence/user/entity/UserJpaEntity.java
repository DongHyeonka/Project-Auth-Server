package com.project.auth.infrastructure.persistence.user.entity;

import com.project.auth.domain.user.model.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, columnDefinition = "text")
    private String email;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, columnDefinition = "text")
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, columnDefinition = "text")
    private String providerSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected UserJpaEntity() {
    }

    private UserJpaEntity(
            UUID id,
            String email,
            String name,
            AuthProvider provider,
            String providerSubject,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.providerSubject = Objects.requireNonNull(providerSubject, "providerSubject must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static UserJpaEntity of(
            UUID id,
            String email,
            String name,
            AuthProvider provider,
            String providerSubject,
            Instant createdAt
    ) {
        return new UserJpaEntity(id, email, name, provider, providerSubject, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
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
