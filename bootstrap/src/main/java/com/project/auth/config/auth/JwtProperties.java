package com.project.auth.config.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "JWT issuer must be an absolute http(s) URL.")
        String issuer,
        String keyId,
        String publicKey,
        String privateKey,
        boolean generateKeyPairOnStartup,
        @NotNull Duration accessTokenExpiration
) {
}
