package com.project.auth.config.auth;

public record JwtKeyProperties(
        String keyId,
        String publicKey,
        String privateKey
) {
}
