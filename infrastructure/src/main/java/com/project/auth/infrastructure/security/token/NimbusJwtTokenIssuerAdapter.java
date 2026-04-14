package com.project.auth.infrastructure.security.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;
import com.project.auth.infrastructure.support.exception.InfrastructureErrorCode;
import com.project.auth.infrastructure.support.exception.InfrastructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class NimbusJwtTokenIssuerAdapter implements IssueLoginTokenPort, IssueOAuthLoginTokenPort {

    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    private static final String TOKEN_TYPE = "Bearer";
    private static final JWSAlgorithm SIGNATURE_ALGORITHM = JWSAlgorithm.RS256;

    private final JWSSigner jwtSigner;
    private final String issuer;
    private final String keyId;
    private final Duration accessTokenExpiration;
    private final Clock clock;

    public NimbusJwtTokenIssuerAdapter(
            String issuer,
            String keyId,
            JWSSigner jwtSigner,
            Duration accessTokenExpiration,
            Clock clock
    ) {
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
        this.jwtSigner = Objects.requireNonNull(jwtSigner, "jwtSigner must not be null");
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

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issueTime(java.util.Date.from(issuedAt))
                .expirationTime(java.util.Date.from(expiresAt))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("provider", user.getProvider().name())
                .build();

        String tokenValue = signToken(claims);
        audit.atInfo()
                .addKeyValue("eventType", "TOKEN_ISSUED")
                .addKeyValue("subject", user.getId())
                .addKeyValue("keyId", keyId)
                .addKeyValue("expiresInSeconds", accessTokenExpiration.getSeconds())
                .log("TOKEN_ISSUED");

        return new IssuedAccessToken(
                issuer,
                tokenValue,
                TOKEN_TYPE,
                accessTokenExpiration.getSeconds(),
                issuedAt,
                expiresAt
        );
    }

    private String signToken(JWTClaimsSet claims) {
        try {
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(SIGNATURE_ALGORITHM)
                            .keyID(keyId)
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims
            );
            signedJWT.sign(jwtSigner);
            return signedJWT.serialize();
        } catch (JOSEException exception) {
            throw new InfrastructureException(InfrastructureErrorCode.JWT_SIGNING_FAILED, "Failed to sign JWT access token.", exception);
        }
    }
}
