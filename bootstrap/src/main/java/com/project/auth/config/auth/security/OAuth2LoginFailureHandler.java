package com.project.auth.config.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.auth.application.auth.exception.AuthErrorCode;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.response.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);
    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    private final ObjectMapper objectMapper;

    public OAuth2LoginFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        log.warn("OAuth2 login failed for {} {}: {}",
                request.getMethod(), request.getRequestURI(), exception.getMessage(), exception);
        audit.warn("OAUTH_AUTHENTICATION_FAILURE method={} uri={} reason={}",
                request.getMethod(), request.getRequestURI(), exception.getMessage());

        response.setStatus(ApiErrorHttpStatusMapper.map(AuthErrorCode.OAUTH_LOGIN_FAILED).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResult.failure(AuthErrorCode.OAUTH_LOGIN_FAILED.code(), AuthErrorCode.OAUTH_LOGIN_FAILED.message())
        );
    }
}
