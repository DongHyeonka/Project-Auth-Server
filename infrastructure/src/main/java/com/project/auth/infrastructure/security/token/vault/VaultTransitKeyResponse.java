package com.project.auth.infrastructure.security.token.vault;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VaultTransitKeyResponse(
        VaultTransitKeyData data,
        List<String> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VaultTransitKeyData(
            @JsonProperty("supports_signing") boolean supportsSigning,
            @JsonProperty("latest_version") int latestVersion,
            Map<String, VaultTransitKeyVersion> keys
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VaultTransitKeyVersion(
            @JsonProperty("public_key") String publicKey
    ) {
    }
}
