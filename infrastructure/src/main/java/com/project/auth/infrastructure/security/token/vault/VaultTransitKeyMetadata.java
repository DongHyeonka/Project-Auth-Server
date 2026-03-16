package com.project.auth.infrastructure.security.token.vault;

public record VaultTransitKeyMetadata(
        int latestVersion,
        String publicKey
) {
}
