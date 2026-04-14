package com.project.auth.infrastructure.security.token.vault;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VaultTransitSignResponse(
        VaultTransitSignData data,
        List<String> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VaultTransitSignData(String signature) {
    }
}
