package com.project.auth.config.auth.security;

import com.project.auth.presentation.auth.current.AuthenticatedUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Objects;

public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter;
    private final KeycloakAuthenticatedUserFactory authenticatedUserFactory;

    public KeycloakJwtAuthenticationConverter(
            Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter,
            KeycloakAuthenticatedUserFactory authenticatedUserFactory
    ) {
        this.authoritiesConverter = Objects.requireNonNull(authoritiesConverter, "authoritiesConverter must not be null");
        this.authenticatedUserFactory = Objects.requireNonNull(
                authenticatedUserFactory,
                "authenticatedUserFactory must not be null"
        );
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
        AuthenticatedUser principal = authenticatedUserFactory.create(jwt, authorities);
        return new KeycloakJwtAuthenticationToken(jwt, authorities, principal, jwt.getSubject());
    }
}
