package com.project.auth.config.auth.security;

import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Objects;

public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

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
        recordAudit(request, AuthAuditEventType.AUTHENTICATION_REQUIRED, "Authentication required.");
        if (response.isCommitted()) {
            log.warn("Response already committed; cannot render authentication error body. requestPath={}",
                    LogSanitizer.requestPath(request.getRequestURI()));
            return;
        }
        handlerExceptionResolver.resolveException(request, response, null, authException);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) {
        recordAudit(request, AuthAuditEventType.ACCESS_DENIED, "Access denied.");
        if (response.isCommitted()) {
            log.warn("Response already committed; cannot render access-denied error body. requestPath={}",
                    LogSanitizer.requestPath(request.getRequestURI()));
            return;
        }
        handlerExceptionResolver.resolveException(request, response, null, accessDeniedException);
    }

    private void recordAudit(HttpServletRequest request, AuthAuditEventType type, String description) {
        try {
            securityAuditTrailWriter.record(request, type, description);
        } catch (RuntimeException auditFailure) {
            log.warn("Security audit write failed; continuing with response rendering. type={}",
                    type, auditFailure);
        }
    }
}
