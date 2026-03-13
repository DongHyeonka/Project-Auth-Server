package com.project.auth.config;

import com.project.auth.application.port.out.PasswordEncoderPort;
import com.project.auth.application.port.out.UserRepositoryPort;
import com.project.auth.application.service.SignUpService;
import com.project.auth.application.usecase.SignUpUseCase;
import com.project.auth.infrastructure.persistence.BcryptPasswordEncoderAdapter;
import com.project.auth.infrastructure.persistence.InMemoryUserRepositoryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class UserCoreConfiguration {

    @Bean
    public UserRepositoryPort userRepositoryPort() {
        return new InMemoryUserRepositoryAdapter();
    }

    @Bean
    public PasswordEncoderPort passwordEncoderPort() {
        return new BcryptPasswordEncoderAdapter();
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SignUpUseCase signUpUseCase(
            UserRepositoryPort userRepositoryPort,
            PasswordEncoderPort passwordEncoderPort,
            Clock systemClock
    ) {
        return new SignUpService(userRepositoryPort, passwordEncoderPort, systemClock);
    }
}
