package com.project.auth.config.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.auth.application.auth.exception.AuthErrorCode;
import com.project.auth.config.logging.LogSanitizer;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.response.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String ANONYMOUS_PRINCIPAL = "anonymous";

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);
    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String actorId = resolveActorId(request);
        String requestPath = LogSanitizer.requestPath(request.getRequestURI());
        log.warn(
                "Authentication required. actorId={} method={} requestPath={}",
                actorId,
                request.getMethod(),
                requestPath
        );
        audit.atWarn()
                .addKeyValue("eventType", "AUTHENTICATION_REQUIRED")
                .addKeyValue("actorId", actorId)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("requestPath", requestPath)
                .log("AUTHENTICATION_REQUIRED");

        HttpStatus status = ApiErrorHttpStatusMapper.map(AuthErrorCode.AUTHENTICATION_REQUIRED);
        writeErrorResponse(response, status, AuthErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        String actorId = resolveActorId(request);
        String requestPath = LogSanitizer.requestPath(request.getRequestURI());
        log.warn(
                "Access denied. actorId={} method={} requestPath={}",
                actorId,
                request.getMethod(),
                requestPath
        );
        audit.atWarn()
                .addKeyValue("eventType", "ACCESS_DENIED")
                .addKeyValue("actorId", actorId)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("requestPath", requestPath)
                .log("ACCESS_DENIED");

        HttpStatus status = ApiErrorHttpStatusMapper.map(AuthErrorCode.ACCESS_DENIED);
        writeErrorResponse(response, status, AuthErrorCode.ACCESS_DENIED);
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            AuthErrorCode errorCode
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResult.failure(errorCode.code(), errorCode.message())
        );
    }

    private String resolveActorId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return LogSanitizer.actorId(principal.getName());
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return LogSanitizer.actorId(authentication.getName());
        }

        return LogSanitizer.actorId(ANONYMOUS_PRINCIPAL);
    }
}
