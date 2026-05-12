package com.project.auth.infrastructure.persistence.user;

import com.project.auth.application.auth.identity.port.out.LoadKeycloakUserPort;
import com.project.auth.application.auth.identity.port.out.RegisterKeycloakUserPort;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.infrastructure.persistence.user.entity.UserJpaEntity;
import com.project.auth.infrastructure.persistence.user.mapper.UserPersistenceMapper;
import com.project.auth.infrastructure.persistence.user.repository.UserJpaRepository;

import java.util.Objects;
import java.util.Optional;

public class JpaUserRepositoryAdapter implements LoadKeycloakUserPort, RegisterKeycloakUserPort {

    private final UserJpaRepository userJpaRepository;
    private final UserPersistenceMapper userPersistenceMapper;

    public JpaUserRepositoryAdapter(UserJpaRepository userJpaRepository, UserPersistenceMapper userPersistenceMapper) {
        this.userJpaRepository = Objects.requireNonNull(userJpaRepository, "userJpaRepository must not be null");
        this.userPersistenceMapper = Objects.requireNonNull(
                userPersistenceMapper,
                "userPersistenceMapper must not be null"
        );
    }

    @Override
    public Optional<User> findByProviderAndProviderSubject(
            AuthProvider provider,
            String providerSubject
    ) {
        return userJpaRepository.findByProviderAndProviderSubject(provider, providerSubject)
                .map(userPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(UserEmail email) {
        UserEmail nonNullEmail = Objects.requireNonNull(email, "email must not be null");

        return userJpaRepository.existsByEmail(nonNullEmail.value());
    }

    @Override
    public User register(User user) {
        return save(user);
    }

    public User save(User user) {
        User nonNullUser = Objects.requireNonNull(user, "user must not be null");

        UserJpaEntity savedUser = userJpaRepository.save(userPersistenceMapper.toEntity(nonNullUser));
        return userPersistenceMapper.toDomain(savedUser);
    }
}
