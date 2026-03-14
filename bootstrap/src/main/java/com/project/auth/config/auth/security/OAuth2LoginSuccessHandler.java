package com.project.auth.config.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.auth.application.auth.exception.UnsupportedOAuthProviderException;
import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.OAuthLoginCommand;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.application.support.code.SuccessCode;
import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.common.response.ApiResult;
import com.project.auth.config.auth.OAuth2LoginProperties;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.presentation.auth.dto.LoginResponse;
import com.project.auth.presentation.auth.mapper.AuthPresentationMapper;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final AuthPresentationMapper authPresentationMapper;
    private final OAuth2LoginProperties oAuth2LoginProperties;
    private final ObjectMapper objectMapper;

    public OAuth2LoginSuccessHandler(
            OAuthLoginUseCase oAuthLoginUseCase,
            AuthPresentationMapper authPresentationMapper,
            OAuth2LoginProperties oAuth2LoginProperties,
            ObjectMapper objectMapper
    ) {
        this.oAuthLoginUseCase = oAuthLoginUseCase;
        this.authPresentationMapper = authPresentationMapper;
        this.oAuth2LoginProperties = oAuth2LoginProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        try {
            OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;
            OidcUser oidcUser = (OidcUser) authenticationToken.getPrincipal();

            OAuthLoginCommand command = new OAuthLoginCommand(
                    resolveProvider(authenticationToken.getAuthorizedClientRegistrationId()),
                    oidcUser.getSubject(),
                    oidcUser.getEmail(),
                    resolveUserName(oidcUser)
            );

            LoginResult loginResult = oAuthLoginUseCase.login(command);
            LoginResponse loginResponse = authPresentationMapper.toResponse(loginResult);

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    ApiResult.success(
                            SuccessCode.AUTH_LOGIN_SUCCEEDED.code(),
                            SuccessCode.AUTH_LOGIN_SUCCEEDED.message(),
                            loginResponse
                    )
            );
            clearAuthenticationAttributes(request);
        } catch (BusinessException exception) {
            response.setStatus(exception.getErrorCode().status());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    ApiResult.failure(exception.getErrorCode().code(), exception.getMessage())
            );
        }
    }

    private AuthProvider resolveProvider(String registrationId) {
        if (oAuth2LoginProperties.googleRegistrationId().equals(registrationId)) {
            return AuthProvider.GOOGLE;
        }

        if (oAuth2LoginProperties.githubRegistrationId().equals(registrationId)) {
            return AuthProvider.GITHUB;
        }

        throw new UnsupportedOAuthProviderException();
    }

    private String resolveUserName(OidcUser oidcUser) {
        if (oidcUser.getFullName() != null && !oidcUser.getFullName().isBlank()) {
            return oidcUser.getFullName();
        }

        if (oidcUser.getPreferredUsername() != null && !oidcUser.getPreferredUsername().isBlank()) {
            return oidcUser.getPreferredUsername();
        }

        return oidcUser.getEmail();
    }
}
