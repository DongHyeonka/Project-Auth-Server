package com.project.auth.infrastructure.persistence.user;

import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.oauth.login.port.out.LoadOAuthUserPort;
import com.project.auth.application.auth.oauth.login.port.out.RegisterOAuthUserPort;
import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.infrastructure.persistence.user.mapper.UserPersistenceMapper;
import com.project.auth.infrastructure.persistence.user.repository.UserJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Objects;
import java.util.Optional;

public class JpaUserRepositoryAdapter implements
        RegisterUserPort,
        LoadLoginUserPort,
        LoadOAuthUserPort,
        RegisterOAuthUserPort {

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
    public boolean existsByEmail(UserEmail email) {
        return userJpaRepository.existsByEmail(email.value());
    }

    @Override
    public Optional<User> findByEmail(UserEmail email) {
        return userJpaRepository.findByEmail(email.value())
                .map(userPersistenceMapper::toDomain);
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
            throw new DuplicateUserEmailException();
        }
    }
}
