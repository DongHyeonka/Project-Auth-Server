package com.project.auth.config.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String OAUTH_LOGIN_COMPLETION_PATH = "/api/v1/auth/oauth2/complete";

    public OAuth2LoginSuccessHandler() {
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        setDefaultTargetUrl(OAUTH_LOGIN_COMPLETION_PATH);
        clearAuthenticationAttributes(request);
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
