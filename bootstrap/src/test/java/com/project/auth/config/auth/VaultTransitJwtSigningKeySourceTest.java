package com.project.auth.config.auth;

import com.project.auth.infrastructure.security.token.vault.VaultTransitClient;
import com.project.auth.infrastructure.security.token.vault.VaultTransitKeyMetadata;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VaultTransitJwtSigningKeySourceTest {

    @Test
    void loadReadsActivePublicKeyFromVaultTransit() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        VaultTransitClient vaultTransitClient = mock(VaultTransitClient.class);
        when(vaultTransitClient.readKey("transit", "project-auth-jwt"))
                .thenReturn(new VaultTransitKeyMetadata(1, toPem((RSAPublicKey) keyPair.getPublic())));

        JwtProperties jwtProperties = new JwtProperties(
                "http://localhost:8080",
                "local-vault-rsa-1",
                false,
                List.of(),
                Duration.ofMinutes(30)
        );
        JwtVaultProperties jwtVaultProperties = new JwtVaultProperties(
                true,
                "http://localhost:8200",
                "project-auth-root-token",
                "transit",
                "project-auth-jwt"
        );

        VaultTransitJwtSigningKeySource source = new VaultTransitJwtSigningKeySource(
                jwtProperties,
                jwtVaultProperties,
                vaultTransitClient
        );

        JwtSigningKeyMaterial keyMaterial = source.load();

        assertThat(keyMaterial.activeKeyId()).isEqualTo("local-vault-rsa-1");
        assertThat(keyMaterial.activePublicJwk().getKeyID()).isEqualTo("local-vault-rsa-1");
        assertThat(keyMaterial.publicJwkSet().getKeys()).hasSize(1);
    }

    private static String toPem(RSAPublicKey publicKey) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }
}
