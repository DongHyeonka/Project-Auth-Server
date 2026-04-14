package com.project.auth.presentation.auth.mapper;

import com.project.auth.application.auth.exception.InvalidOAuthUserInfoException;
import com.project.auth.application.auth.oauth.login.OAuthLoginCommand;
import com.project.auth.presentation.auth.current.OAuth2LoginPrincipal;
import org.springframework.stereotype.Component;

@Component
public class OAuth2AuthenticationCommandMapper {

    public OAuthLoginCommand toCommand(OAuth2LoginPrincipal principal) {
        if (principal == null) {
            throw new InvalidOAuthUserInfoException();
        }

        return new OAuthLoginCommand(
                principal.provider(),
                principal.providerSubject(),
                principal.email(),
                principal.name()
        );
    }
}
