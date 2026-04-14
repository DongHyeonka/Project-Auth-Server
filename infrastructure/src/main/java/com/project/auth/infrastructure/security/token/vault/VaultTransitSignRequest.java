package com.project.auth.infrastructure.security.token.vault;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultTransitSignRequest(
        String input,
        @JsonProperty("signature_algorithm") String signatureAlgorithm,
        @JsonProperty("hash_algorithm") String hashAlgorithm
) {
}
