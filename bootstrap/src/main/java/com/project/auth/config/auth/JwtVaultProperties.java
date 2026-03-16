package com.project.auth.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt.vault")
public record JwtVaultProperties(
        boolean enabled,
        String address,
        String token,
        String mountPath,
        String transitKeyName
) {
}
