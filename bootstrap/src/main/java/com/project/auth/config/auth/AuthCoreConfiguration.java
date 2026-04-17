package com.project.auth.config.auth;

import com.project.auth.application.auth.resource.SyncKeycloakUserUseCase;
import com.project.auth.application.auth.resource.internal.KeycloakUserSynchronizer;
import com.project.auth.application.auth.resource.port.out.LoadKeycloakUserPort;
import com.project.auth.application.auth.resource.port.out.RegisterKeycloakUserPort;
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
    public SyncKeycloakUserUseCase syncKeycloakUserUseCase(
            LoadKeycloakUserPort loadKeycloakUserPort,
            RegisterKeycloakUserPort registerKeycloakUserPort,
            Clock systemClock,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        return new KeycloakUserSynchronizer(
                loadKeycloakUserPort,
                registerKeycloakUserPort,
                systemClock,
                authAuditEventPublisher
        );
    }
}
