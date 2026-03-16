package com.project.auth.openid;

import com.project.auth.config.auth.JwtProperties;
import com.project.auth.config.auth.JwtSigningKeyMaterial;
import com.project.auth.presentation.openid.OpenIdDiscoveryDocumentProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ConfiguredOpenIdDiscoveryDocumentProvider implements OpenIdDiscoveryDocumentProvider {

    private static final String JWK_SET_PATH = "/.well-known/jwks.json";

    private final JwtProperties jwtProperties;
    private final JwtSigningKeyMaterial jwtSigningKeyMaterial;

    public ConfiguredOpenIdDiscoveryDocumentProvider(
            JwtProperties jwtProperties,
            JwtSigningKeyMaterial jwtSigningKeyMaterial
    ) {
        this.jwtProperties = jwtProperties;
        this.jwtSigningKeyMaterial = jwtSigningKeyMaterial;
    }

    @Override
    public Map<String, Object> openIdConfiguration() {
        String issuer = normalizeIssuer(jwtProperties.issuer());

        return Map.of(
                "issuer", issuer,
                "jwks_uri", issuer + JWK_SET_PATH,
                "id_token_signing_alg_values_supported", List.of("RS256"),
                "subject_types_supported", List.of("public")
        );
    }

    @Override
    public Map<String, Object> jwkSet() {
        return jwtSigningKeyMaterial.publicJwkSet().toJSONObject();
    }

    private static String normalizeIssuer(String issuer) {
        if (issuer.endsWith("/")) {
            return issuer.substring(0, issuer.length() - 1);
        }

        return issuer;
    }
}
