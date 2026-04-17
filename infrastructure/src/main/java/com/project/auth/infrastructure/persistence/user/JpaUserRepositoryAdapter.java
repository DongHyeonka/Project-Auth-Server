package com.project.auth.infrastructure.persistence.user;

import com.project.auth.application.auth.exception.KeycloakAccountConflictException;
import com.project.auth.application.auth.resource.port.out.LoadKeycloakUserPort;
import com.project.auth.application.auth.resource.port.out.RegisterKeycloakUserPort;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import com.project.auth.infrastructure.persistence.user.mapper.UserPersistenceMapper;
import com.project.auth.infrastructure.persistence.user.repository.UserJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;

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
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject) {
        return userJpaRepository.findByProviderAndProviderSubject(provider, providerSubject)
                .map(userPersistenceMapper::toDomain);
    }

    @Override
    public User save(User user) {
        try {
            return userPersistenceMapper.toDomain(
                    userJpaRepository.saveAndFlush(userPersistenceMapper.toEntity(user))
            );
        } catch (DataIntegrityViolationException exception) {
            throw new KeycloakAccountConflictException();
        }
    }
}
