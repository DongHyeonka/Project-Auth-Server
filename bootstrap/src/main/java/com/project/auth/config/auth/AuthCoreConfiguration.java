package com.project.auth.config.auth;

import com.project.auth.application.auth.identity.LoadKeycloakUserUseCase;
import com.project.auth.application.auth.identity.internal.KeycloakUserLoader;
import com.project.auth.application.auth.identity.port.out.LoadKeycloakUserPort;
import com.project.auth.application.auth.identity.port.out.RegisterKeycloakUserPort;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuthCoreConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public LoadKeycloakUserUseCase loadKeycloakUserUseCase(
            LoadKeycloakUserPort loadKeycloakUserPort,
            RegisterKeycloakUserPort registerKeycloakUserPort,
            AuthAuditEventPublisher authAuditEventPublisher,
            Clock clock
    ) {
        return new KeycloakUserLoader(
                loadKeycloakUserPort,
                registerKeycloakUserPort,
                authAuditEventPublisher,
                clock
        );
    }
}
