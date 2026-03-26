package com.project.auth.presentation.auth.mapper;

import com.project.auth.application.auth.exception.InvalidOAuthUserInfoException;
import com.project.auth.application.auth.oauth.login.OAuthLoginCommand;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AuthenticationCommandMapper {

    public OAuthLoginCommand toCommand(Authentication authentication) {
        OAuth2AuthenticationToken authenticationToken = requireAuthenticationToken(authentication);
        OidcUser oidcUser = requireOidcUser(authenticationToken.getPrincipal());

        return new OAuthLoginCommand(
                "KEYCLOAK",
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                resolveUserName(oidcUser)
        );
    }

    private OAuth2AuthenticationToken requireAuthenticationToken(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken authenticationToken) {
            return authenticationToken;
        }

        throw new InvalidOAuthUserInfoException();
    }

    private OidcUser requireOidcUser(Object principal) {
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser;
        }

        throw new InvalidOAuthUserInfoException();
    }

    private String resolveUserName(OidcUser oidcUser) {
        String name = oidcUser.getFullName();
        return (name != null && !name.isBlank()) ? name : oidcUser.getPreferredUsername();
    }
}
