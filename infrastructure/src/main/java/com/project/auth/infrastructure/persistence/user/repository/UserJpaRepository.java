package com.project.auth.infrastructure.persistence.user.repository;

import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.infrastructure.persistence.user.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    boolean existsByEmail(String email);

    Optional<UserJpaEntity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
