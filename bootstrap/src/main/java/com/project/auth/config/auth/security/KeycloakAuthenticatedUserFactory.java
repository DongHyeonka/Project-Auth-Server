package com.project.auth.config.auth.security;

import com.project.auth.presentation.auth.current.AuthenticatedUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class KeycloakAuthenticatedUserFactory {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";

    public AuthenticatedUser create(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        return new AuthenticatedUser(
                jwt.getSubject(),
                jwt.getClaimAsString(CLAIM_EMAIL),
                displayName(jwt),
                authorityNames(authorities)
        );
    }

    private static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString(CLAIM_NAME);
        if (name != null && !name.isBlank()) {
            return name;
        }
        return jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
    }

    private static Set<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
