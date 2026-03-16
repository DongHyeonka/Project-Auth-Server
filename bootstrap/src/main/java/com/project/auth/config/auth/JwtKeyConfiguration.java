package com.project.auth.config.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfiguration {

    @Bean
    public JwtSigningKeyMaterial jwtSigningKeyMaterial(JwtSigningKeySource jwtSigningKeySource) {
        return jwtSigningKeySource.load();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtSigningKeyMaterial jwtSigningKeyMaterial) {
        return new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(jwtSigningKeyMaterial.activePrivateJwk()))
        );
    }
}
