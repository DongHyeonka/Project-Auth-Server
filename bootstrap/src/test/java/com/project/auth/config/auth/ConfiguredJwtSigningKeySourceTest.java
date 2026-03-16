package com.project.auth.config.auth;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredJwtSigningKeySourceTest {

    @Test
    void loadReturnsActiveSigningKeyAndAllPublicKeysForRotation() throws Exception {
        KeyPair previousKeyPair = generateKeyPair();
        KeyPair currentKeyPair = generateKeyPair();

        JwtProperties jwtProperties = new JwtProperties(
                "https://auth.example.com",
                "auth-rsa-2",
                false,
                List.of(
                        new JwtKeyProperties(
                                "auth-rsa-1",
                                encodePublicKey(previousKeyPair),
                                null
                        ),
                        new JwtKeyProperties(
                                "auth-rsa-2",
                                encodePublicKey(currentKeyPair),
                                encodePrivateKey(currentKeyPair)
                        )
                ),
                Duration.ofMinutes(30)
        );

        ConfiguredJwtSigningKeySource source = new ConfiguredJwtSigningKeySource(jwtProperties);

        JwtSigningKeyMaterial keyMaterial = source.load();

        assertThat(keyMaterial.activeKeyId()).isEqualTo("auth-rsa-2");
        assertThat(keyMaterial.activePrivateJwk().getKeyID()).isEqualTo("auth-rsa-2");
        assertThat(keyMaterial.publicJwkSet().getKeys()).hasSize(2);
        assertThat(keyMaterial.publicJwkSet().getKeys())
                .extracting(key -> key.getKeyID())
                .containsExactlyInAnyOrder("auth-rsa-1", "auth-rsa-2");
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private static String encodePublicKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(((RSAPublicKey) keyPair.getPublic()).getEncoded());
    }

    private static String encodePrivateKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(((RSAPrivateKey) keyPair.getPrivate()).getEncoded());
    }
}
