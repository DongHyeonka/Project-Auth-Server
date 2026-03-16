package com.project.auth.config.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.security.jwt.vault", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ConfiguredJwtSigningKeySource implements JwtSigningKeySource {

    private static final int RSA_KEY_SIZE = 2048;

    private final ResolvedJwtKeyMaterial resolvedJwtKeyMaterial;

    public ConfiguredJwtSigningKeySource(JwtProperties jwtProperties) {
        this.resolvedJwtKeyMaterial = resolveKeyMaterial(jwtProperties);
    }

    @Override
    public JwtSigningKeyMaterial load() {
        return new JwtSigningKeyMaterial(
                resolvedJwtKeyMaterial.activeKeyId(),
                resolvedJwtKeyMaterial.activePublicJwk(),
                resolvedJwtKeyMaterial.publicJwkSet()
        );
    }

    public RSAKey activeSigningKey() {
        return resolvedJwtKeyMaterial.activePrivateJwk();
    }

    private ResolvedJwtKeyMaterial resolveKeyMaterial(JwtProperties jwtProperties) {
        if (jwtProperties.keys().isEmpty() && jwtProperties.generateKeyPairOnStartup()) {
            return generateSingleLocalKeyMaterial(jwtProperties.activeKeyId());
        }

        Map<String, RSAKey> keysById = jwtProperties.keys().stream()
                .map(this::parseConfiguredKey)
                .collect(Collectors.toMap(RSAKey::getKeyID, Function.identity()));

        if (!keysById.containsKey(jwtProperties.activeKeyId())) {
            throw new IllegalStateException("Active JWT key id is not present in configured key set.");
        }

        RSAKey activeKey = keysById.get(jwtProperties.activeKeyId());
        if (!activeKey.isPrivate()) {
            throw new IllegalStateException("Active JWT key must include a private key.");
        }

        List<JWK> publicKeys = keysById.values().stream()
                .map(key -> (JWK) key.toPublicJWK())
                .toList();

        return new ResolvedJwtKeyMaterial(
                jwtProperties.activeKeyId(),
                activeKey,
                (RSAKey) activeKey.toPublicJWK(),
                new JWKSet(publicKeys)
        );
    }

    private ResolvedJwtKeyMaterial generateSingleLocalKeyMaterial(String activeKeyId) {
        KeyPair keyPair = generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey activeKey = buildRsaKey(publicKey, privateKey, activeKeyId);

        return new ResolvedJwtKeyMaterial(
                activeKeyId,
                activeKey,
                (RSAKey) activeKey.toPublicJWK(),
                new JWKSet(activeKey.toPublicJWK())
        );
    }

    private RSAKey parseConfiguredKey(JwtKeyProperties keyProperties) {
        if (!StringUtils.hasText(keyProperties.keyId())) {
            throw new IllegalStateException("Configured JWT key id must not be blank.");
        }
        if (!StringUtils.hasText(keyProperties.publicKey())) {
            throw new IllegalStateException("Configured JWT public key must not be blank.");
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(decodeKeyMaterial(keyProperties.publicKey()))
            );
            RSAPrivateKey privateKey = StringUtils.hasText(keyProperties.privateKey())
                    ? (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(decodeKeyMaterial(keyProperties.privateKey()))
            )
                    : null;

            return buildRsaKey(publicKey, privateKey, keyProperties.keyId());
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Failed to parse configured RSA key pair.", exception);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(RSA_KEY_SIZE);
            return keyPairGenerator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to generate RSA key pair for local startup.", exception);
        }
    }

    private static RSAKey buildRsaKey(RSAPublicKey publicKey, RSAPrivateKey privateKey, String keyId) {
        RSAKey.Builder builder = new RSAKey.Builder(publicKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.parse(SignatureAlgorithm.RS256.getName()))
                .keyID(keyId);

        if (privateKey != null) {
            builder.privateKey(privateKey);
        }

        return builder.build();
    }

    private static byte[] decodeKeyMaterial(String keyMaterial) {
        String normalized = keyMaterial
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        return Base64.getDecoder().decode(normalized);
    }

    private record ResolvedJwtKeyMaterial(
            String activeKeyId,
            RSAKey activePrivateJwk,
            RSAKey activePublicJwk,
            JWKSet publicJwkSet
    ) {
    }
}
