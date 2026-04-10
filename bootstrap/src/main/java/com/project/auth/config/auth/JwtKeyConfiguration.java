package com.project.auth.config.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.JWSSigner;
import com.project.auth.infrastructure.security.token.vault.VaultTransitClient;
import com.project.auth.infrastructure.security.token.vault.VaultTransitJwtSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, JwtVaultProperties.class})
public class JwtKeyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfiguration.class);
    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    @Bean
    public JwtSigningKeyMaterial jwtSigningKeyMaterial(JwtSigningKeySource jwtSigningKeySource) {
        log.info("JWT key source selected: {}", jwtSigningKeySource.getClass().getSimpleName());
        JwtSigningKeyMaterial material = jwtSigningKeySource.load();
        audit.info("KEY_SOURCE_SELECTED source={} activeKeyId={}",
                jwtSigningKeySource.getClass().getSimpleName(), material.activeKeyId());
        return material;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.security.jwt.vault", name = "enabled", havingValue = "false", matchIfMissing = true)
    public JWSSigner jwtSigner(ConfiguredJwtSigningKeySource configuredJwtSigningKeySource) {
        try {
            return new RSASSASigner(configuredJwtSigningKeySource.activeSigningKey());
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to create JWT signer from configured signing key.", exception);
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.security.jwt.vault", name = "enabled", havingValue = "true")
    public HttpClient vaultHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.security.jwt.vault", name = "enabled", havingValue = "true")
    public VaultTransitClient vaultTransitClient(
            JwtVaultProperties jwtVaultProperties,
            HttpClient vaultHttpClient,
            ObjectMapper objectMapper
    ) {
        return new VaultTransitClient(
                jwtVaultProperties.address(),
                jwtVaultProperties.token(),
                vaultHttpClient,
                objectMapper
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.security.jwt.vault", name = "enabled", havingValue = "true")
    public JWSSigner vaultTransitJwtSigner(
            JwtVaultProperties jwtVaultProperties,
            VaultTransitClient vaultTransitClient
    ) {
        return new VaultTransitJwtSigner(
                vaultTransitClient,
                jwtVaultProperties.mountPath(),
                jwtVaultProperties.transitKeyName()
        );
    }
}
