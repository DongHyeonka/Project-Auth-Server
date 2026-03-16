package com.project.auth.config.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
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

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfiguration {

    private static final int RSA_KEY_SIZE = 2048;

    @Bean
    public JwtSigningKeyMaterial jwtSigningKeyMaterial(JwtProperties jwtProperties) {
        KeyPair keyPair = resolveKeyPair(jwtProperties);
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        String keyId = resolveKeyId(jwtProperties.keyId(), publicKey, privateKey);
        RSAKey privateJwk = buildPrivateJwk(publicKey, privateKey, keyId);

        return new JwtSigningKeyMaterial(
                keyId,
                privateJwk,
                new JWKSet(privateJwk.toPublicJWK())
        );
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtSigningKeyMaterial jwtSigningKeyMaterial) {
        return new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(jwtSigningKeyMaterial.privateJwk()))
        );
    }

    private static KeyPair resolveKeyPair(JwtProperties jwtProperties) {
        boolean hasPublicKey = StringUtils.hasText(jwtProperties.publicKey());
        boolean hasPrivateKey = StringUtils.hasText(jwtProperties.privateKey());

        if (hasPublicKey && hasPrivateKey) {
            return parseConfiguredKeyPair(jwtProperties.publicKey(), jwtProperties.privateKey());
        }

        if (jwtProperties.generateKeyPairOnStartup()) {
            return generateKeyPair();
        }

        throw new IllegalStateException(
                "JWT RSA key pair must be configured when generate-key-pair-on-startup is false."
        );
    }

    private static KeyPair parseConfiguredKeyPair(String publicKey, String privateKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey rsaPublicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(decodeKeyMaterial(publicKey))
            );
            RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(decodeKeyMaterial(privateKey))
            );
            return new KeyPair(rsaPublicKey, rsaPrivateKey);
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

    private static String resolveKeyId(String configuredKeyId, RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        if (StringUtils.hasText(configuredKeyId)) {
            return configuredKeyId;
        }

        try {
            return buildPrivateJwk(publicKey, privateKey, null)
                    .toPublicJWK()
                    .computeThumbprint()
                    .toString();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to derive JWT key id from RSA public key.", exception);
        }
    }

    private static RSAKey buildPrivateJwk(RSAPublicKey publicKey, RSAPrivateKey privateKey, String keyId) {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.parse(SignatureAlgorithm.RS256.getName()))
                .keyID(keyId)
                .build();
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
}
