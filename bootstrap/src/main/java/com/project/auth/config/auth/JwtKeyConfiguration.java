package com.project.auth.config.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.JWSSigner;
import com.project.auth.infrastructure.security.token.vault.VaultTransitClient;
import com.project.auth.infrastructure.security.token.vault.VaultTransitJwtSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, JwtVaultProperties.class})
public class JwtKeyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfiguration.class);
    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    @Bean
    public JwtSigningKeyMaterial jwtSigningKeyMaterial(JwtSigningKeySource jwtSigningKeySource) {
        log.info("JWT key source selected: {}", jwtSigningKeySource.getClass().getSimpleName());
        JwtSigningKeyMaterial material = jwtSigningKeySource.load();
        audit.atInfo()
                .addKeyValue("eventType", "KEY_SOURCE_SELECTED")
                .addKeyValue("source", jwtSigningKeySource.getClass().getSimpleName())
                .addKeyValue("activeKeyId", material.activeKeyId())
                .log("KEY_SOURCE_SELECTED");
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
    public VaultTransitClient vaultTransitClient(
            JwtVaultProperties jwtVaultProperties,
            RestClient.Builder restClientBuilder
    ) {
        RestClient vaultRestClient = restClientBuilder
                .baseUrl(jwtVaultProperties.address())
                .defaultHeader("X-Vault-Token", jwtVaultProperties.token())
                .build();
        return new VaultTransitClient(vaultRestClient);
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
