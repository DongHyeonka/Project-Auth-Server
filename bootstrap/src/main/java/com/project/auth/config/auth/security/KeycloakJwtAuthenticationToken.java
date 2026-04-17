package com.project.auth.config.auth.security;

import com.project.auth.presentation.auth.current.AuthenticatedUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

public class KeycloakJwtAuthenticationToken extends JwtAuthenticationToken {

    private final AuthenticatedUser principal;

    public KeycloakJwtAuthenticationToken(
            Jwt jwt,
            Collection<? extends GrantedAuthority> authorities,
            AuthenticatedUser principal,
            String name
    ) {
        super(jwt, authorities, name);
        this.principal = principal;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }
}
