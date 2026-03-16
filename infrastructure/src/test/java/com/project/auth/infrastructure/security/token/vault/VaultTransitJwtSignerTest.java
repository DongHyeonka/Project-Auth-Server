package com.project.auth.infrastructure.security.token.vault;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VaultTransitJwtSignerTest {

    @Test
    void signDelegatesToVaultTransitAndReturnsJoseSignature() throws Exception {
        byte[] rawSignature = "signed-by-vault".getBytes(StandardCharsets.UTF_8);

        VaultTransitClient vaultTransitClient = mock(VaultTransitClient.class);
        when(vaultTransitClient.sign("transit", "project-auth-jwt", "header.payload".getBytes(StandardCharsets.UTF_8)))
                .thenReturn("vault:v1:" + Base64.getEncoder().encodeToString(rawSignature));

        VaultTransitJwtSigner signer = new VaultTransitJwtSigner(
                vaultTransitClient,
                "transit",
                "project-auth-jwt"
        );

        Base64URL signature = signer.sign(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                "header.payload".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(signature.decode()).isEqualTo(rawSignature);
    }
}
