package com.project.auth.infrastructure.security.token;

import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class NimbusJwtTokenIssuerAdapter implements IssueLoginTokenPort, IssueOAuthLoginTokenPort {

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String keyId;
    private final Duration accessTokenExpiration;
    private final Clock clock;

    public NimbusJwtTokenIssuerAdapter(
            String issuer,
            String keyId,
            JwtEncoder jwtEncoder,
            Duration accessTokenExpiration,
            Clock clock
    ) {
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder must not be null");
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
                JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256)
                                .keyId(keyId)
                                .build(),
                        claims
                )
        ).getTokenValue();

        return new IssuedAccessToken(
                issuer,
                tokenValue,
                TOKEN_TYPE,
                accessTokenExpiration.getSeconds(),
                issuedAt,
                expiresAt
        );
    }
}
