package com.project.auth.config.persistence;

import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
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
    public RegisterUserPort registerUserPort(JpaUserRepositoryAdapter jpaUserRepositoryAdapter) {
        return jpaUserRepositoryAdapter;
    }

    @Bean
    public LoadLoginUserPort loadLoginUserPort(JpaUserRepositoryAdapter jpaUserRepositoryAdapter) {
        return jpaUserRepositoryAdapter;
    }
}
