package com.project.auth.config.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.config.logging.LogSanitizer;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.response.ApiResultFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);
    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    private final ObjectMapper objectMapper;
    private final ApiResultFactory apiResultFactory;

    public OAuth2LoginFailureHandler(ObjectMapper objectMapper, ApiResultFactory apiResultFactory) {
        this.objectMapper = objectMapper;
        this.apiResultFactory = apiResultFactory;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String requestPath = LogSanitizer.requestPath(request.getRequestURI());
        String reason = failureReason(exception);
        log.warn("OAuth2 login failed. method={} requestPath={} reason={}",
                request.getMethod(), requestPath, reason);
        audit.atWarn()
                .addKeyValue(AuthAuditFields.EVENT_TYPE, AuthAuditEventType.OAUTH_AUTHENTICATION_FAILURE.code())
                .addKeyValue(AuthAuditFields.METHOD, request.getMethod())
                .addKeyValue(AuthAuditFields.REQUEST_PATH, requestPath)
                .addKeyValue(AuthAuditFields.REASON, reason)
                .log(AuthAuditEventType.OAUTH_AUTHENTICATION_FAILURE.code());

        response.setStatus(ApiErrorHttpStatusMapper.map(AuthErrorCode.OAUTH_LOGIN_FAILED).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                apiResultFactory.failure(AuthErrorCode.OAUTH_LOGIN_FAILED.code(), AuthErrorCode.OAUTH_LOGIN_FAILED.message())
        );
    }

    private String failureReason(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            return LogSanitizer.reason(oauth2Exception.getError().getErrorCode());
        }
        return "authentication_failed";
    }
}
