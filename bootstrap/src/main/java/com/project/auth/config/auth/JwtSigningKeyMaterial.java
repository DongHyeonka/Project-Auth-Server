package com.project.auth.config.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

public record JwtSigningKeyMaterial(
        String activeKeyId,
        RSAKey activePublicJwk,
        JWKSet publicJwkSet
) {
}
