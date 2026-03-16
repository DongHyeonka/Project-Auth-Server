package com.project.auth.infrastructure.security.token;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.SignedJWT;
import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NimbusJwtTokenIssuerAdapterTest {

    @Test
    void issueCreatesRs256SignedTokenWithConfiguredKeyId() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("auth-server-rsa-1")
                .keyUse(KeyUse.SIGNATURE)
                .build();

        JWSSigner jwtSigner = new RSASSASigner(rsaKey);
        NimbusJwtTokenIssuerAdapter adapter = new NimbusJwtTokenIssuerAdapter(
                "https://auth.example.com",
                "auth-server-rsa-1",
                jwtSigner,
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-03-15T00:00:00Z"), ZoneOffset.UTC)
        );

        User user = User.registerLocal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UserEmail.from("tester@example.com"),
                "encoded-password123",
                UserName.from("테스터"),
                Instant.parse("2026-03-14T00:00:00Z")
        );

        IssuedAccessToken issuedAccessToken = adapter.issue(user);
        SignedJWT signedJWT = SignedJWT.parse(issuedAccessToken.accessToken());

        assertThat(issuedAccessToken.issuer()).isEqualTo("https://auth.example.com");
        assertThat(signedJWT.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(signedJWT.getHeader().getKeyID()).isEqualTo("auth-server-rsa-1");
        assertThat(signedJWT.getJWTClaimsSet().getIssuer()).isEqualTo("https://auth.example.com");
        assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("email")).isEqualTo("tester@example.com");
    }
}
