package com.project.auth.config.auth.security;

import com.project.auth.application.support.audit.AuthAuditEventType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Objects;

public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;
    private final SecurityAuditTrailWriter securityAuditTrailWriter;

    public SecurityExceptionHandler(
            HandlerExceptionResolver handlerExceptionResolver,
            SecurityAuditTrailWriter securityAuditTrailWriter
    ) {
        this.handlerExceptionResolver = Objects.requireNonNull(
                handlerExceptionResolver,
                "handlerExceptionResolver must not be null"
        );
        this.securityAuditTrailWriter = Objects.requireNonNull(
                securityAuditTrailWriter,
                "securityAuditTrailWriter must not be null"
        );
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) {
        securityAuditTrailWriter.record(request, AuthAuditEventType.AUTHENTICATION_REQUIRED, "Authentication required.");
        handlerExceptionResolver.resolveException(request, response, null, authException);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) {
        securityAuditTrailWriter.record(request, AuthAuditEventType.ACCESS_DENIED, "Access denied.");
        handlerExceptionResolver.resolveException(request, response, null, accessDeniedException);
    }
}
