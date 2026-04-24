package com.project.auth.config.persistence;

import com.project.auth.application.auth.identity.port.out.LoadKeycloakUserPort;
import com.project.auth.infrastructure.persistence.user.JpaUserRepositoryAdapter;
import com.project.auth.infrastructure.persistence.user.mapper.UserPersistenceMapper;
import com.project.auth.infrastructure.persistence.user.repository.UserJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceConfiguration {

    @Bean
    public UserPersistenceMapper userPersistenceMapper() {
        return new UserPersistenceMapper();
    }

    @Bean
    public JpaUserRepositoryAdapter jpaUserRepositoryAdapter(
            UserJpaRepository userJpaRepository,
            UserPersistenceMapper userPersistenceMapper
    ) {
        return new JpaUserRepositoryAdapter(userJpaRepository, userPersistenceMapper);
    }

    @Bean
    public LoadKeycloakUserPort loadKeycloakUserPort(JpaUserRepositoryAdapter jpaUserRepositoryAdapter) {
        return jpaUserRepositoryAdapter;
    }
}
