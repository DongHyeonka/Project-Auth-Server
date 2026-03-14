package com.project.auth.infrastructure.security.token;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class NimbusJwtTokenIssuerAdapter implements IssueLoginTokenPort, IssueOAuthLoginTokenPort {

    private static final String TOKEN_TYPE = "Bearer";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenExpiration;
    private final Clock clock;

    public NimbusJwtTokenIssuerAdapter(
            String issuer,
            String secret,
            Duration accessTokenExpiration,
            Clock clock
    ) {
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(createSecretKey(secret)));
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.accessTokenExpiration = Objects.requireNonNull(
                accessTokenExpiration,
                "accessTokenExpiration must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public IssuedAccessToken issue(User user) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(accessTokenExpiration);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("provider", user.getProvider().name())
                .build();

        String tokenValue = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();

        return new IssuedAccessToken(
                tokenValue,
                TOKEN_TYPE,
                accessTokenExpiration.getSeconds(),
                issuedAt,
                expiresAt
        );
    }

    private static SecretKey createSecretKey(String secret) {
        Objects.requireNonNull(secret, "secret must not be null");

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256.");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
