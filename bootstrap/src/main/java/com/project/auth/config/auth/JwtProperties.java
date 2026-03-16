package com.project.auth.config.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "JWT issuer must be an absolute http(s) URL.")
        String issuer,
        @NotBlank String activeKeyId,
        boolean generateKeyPairOnStartup,
        List<JwtKeyProperties> keys,
        @NotNull Duration accessTokenExpiration
) {

    public JwtProperties {
        keys = keys == null ? List.of() : List.copyOf(keys);
    }
}
