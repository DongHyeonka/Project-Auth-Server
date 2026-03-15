package com.project.auth.config.auth.security;

import com.project.auth.application.auth.exception.UnsupportedOAuthProviderException;
import com.project.auth.config.auth.OAuth2LoginProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String OAUTH_LOGIN_COMPLETION_PATH = "/api/v1/auth/oauth2/complete";

    private final OAuth2LoginProperties oAuth2LoginProperties;

    public OAuth2LoginSuccessHandler(OAuth2LoginProperties oAuth2LoginProperties) {
        this.oAuth2LoginProperties = oAuth2LoginProperties;
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;
        setDefaultTargetUrl(
                UriComponentsBuilder.fromPath(OAUTH_LOGIN_COMPLETION_PATH)
                        .queryParam("provider", resolveProvider(authenticationToken.getAuthorizedClientRegistrationId()))
                        .build()
                        .toUriString()
        );
        clearAuthenticationAttributes(request);
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private String resolveProvider(String registrationId) {
        if (oAuth2LoginProperties.googleRegistrationId().equals(registrationId)) {
            return "GOOGLE";
        }

        if (oAuth2LoginProperties.githubRegistrationId().equals(registrationId)) {
            return "GITHUB";
        }

        throw new UnsupportedOAuthProviderException();
    }
}
