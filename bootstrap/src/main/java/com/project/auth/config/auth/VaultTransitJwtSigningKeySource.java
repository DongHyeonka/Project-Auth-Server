package com.project.auth.config.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.project.auth.infrastructure.security.token.vault.VaultTransitClient;
import com.project.auth.infrastructure.security.token.vault.VaultTransitKeyMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
@ConditionalOnProperty(prefix = "app.security.jwt.vault", name = "enabled", havingValue = "true")
public class VaultTransitJwtSigningKeySource implements JwtSigningKeySource {

    private static final Logger log = LoggerFactory.getLogger(VaultTransitJwtSigningKeySource.class);

    private final JwtProperties jwtProperties;
    private final JwtVaultProperties jwtVaultProperties;
    private final VaultTransitClient vaultTransitClient;

    public VaultTransitJwtSigningKeySource(
            JwtProperties jwtProperties,
            JwtVaultProperties jwtVaultProperties,
            VaultTransitClient vaultTransitClient
    ) {
        this.jwtProperties = jwtProperties;
        this.jwtVaultProperties = jwtVaultProperties;
        this.vaultTransitClient = vaultTransitClient;
    }

    @Override
    public JwtSigningKeyMaterial load() {
        log.info("Loading JWT signing key from Vault Transit: mountPath={}, keyName={}",
                jwtVaultProperties.mountPath(), jwtVaultProperties.transitKeyName());

        VaultTransitKeyMetadata keyMetadata = vaultTransitClient.readKey(
                jwtVaultProperties.mountPath(),
                jwtVaultProperties.transitKeyName()
        );
        RSAKey publicJwk = toPublicJwk(keyMetadata.publicKey());

        log.info("Vault Transit JWT key loaded successfully: activeKeyId={}, version={}",
                jwtProperties.activeKeyId(), keyMetadata.latestVersion());

        return new JwtSigningKeyMaterial(
                jwtProperties.activeKeyId(),
                publicJwk,
                new JWKSet(publicJwk)
        );
    }

    private RSAKey toPublicJwk(String pemPublicKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(decodePem(pemPublicKey))
            );

            return new RSAKey.Builder(publicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.parse(SignatureAlgorithm.RS256.getName()))
                    .keyID(jwtProperties.activeKeyId())
                    .build();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to parse Vault transit public key.", exception);
        }
    }

    private static byte[] decodePem(String pemPublicKey) {
        String normalized = pemPublicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        return Base64.getDecoder().decode(normalized);
    }
}
