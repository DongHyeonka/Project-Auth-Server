package com.project.auth.infrastructure.security.token.vault;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.impl.BaseJWSProvider;
import com.nimbusds.jose.util.Base64URL;

import java.util.Base64;
import java.util.Set;

public class VaultTransitJwtSigner extends BaseJWSProvider implements JWSSigner {

    private final VaultTransitClient vaultTransitClient;
    private final String mountPath;
    private final String transitKeyName;

    public VaultTransitJwtSigner(
            VaultTransitClient vaultTransitClient,
            String mountPath,
            String transitKeyName
    ) {
        super(Set.of(JWSAlgorithm.RS256));
        this.vaultTransitClient = vaultTransitClient;
        this.mountPath = mountPath;
        this.transitKeyName = transitKeyName;
    }

    @Override
    public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
        if (!JWSAlgorithm.RS256.equals(header.getAlgorithm())) {
            throw new JOSEException("Vault transit signer only supports RS256.");
        }

        String vaultSignature = vaultTransitClient.sign(mountPath, transitKeyName, signingInput);
        return Base64URL.encode(decodeVaultSignature(vaultSignature));
    }

    private static byte[] decodeVaultSignature(String vaultSignature) throws JOSEException {
        String[] parts = vaultSignature.split(":", 3);
        if (parts.length != 3 || !"vault".equals(parts[0])) {
            throw new JOSEException("Unexpected Vault transit signature format.");
        }

        try {
            return Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw new JOSEException("Failed to decode Vault transit signature.", exception);
        }
    }
}
