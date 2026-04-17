package com.project.auth.config.auth.security;

import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.config.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Objects;

public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String ANONYMOUS_PRINCIPAL = "anonymous";

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);
    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityExceptionHandler(HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = Objects.requireNonNull(
                handlerExceptionResolver,
                "handlerExceptionResolver must not be null"
        );
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) {
        recordAudit(request, AuthAuditEventType.AUTHENTICATION_REQUIRED, "Authentication required.");
        handlerExceptionResolver.resolveException(request, response, null, authException);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) {
        recordAudit(request, AuthAuditEventType.ACCESS_DENIED, "Access denied.");
        handlerExceptionResolver.resolveException(request, response, null, accessDeniedException);
    }

    private void recordAudit(HttpServletRequest request, AuthAuditEventType eventType, String message) {
        String actorId = resolveActorId(request);
        String requestPath = LogSanitizer.requestPath(request.getRequestURI());
        log.warn(
                "{} actorId={} method={} requestPath={}",
                message,
                actorId,
                request.getMethod(),
                requestPath
        );
        audit.atWarn()
                .addKeyValue(AuthAuditFields.EVENT_TYPE, eventType.code())
                .addKeyValue(AuthAuditFields.ACTOR_ID, actorId)
                .addKeyValue(AuthAuditFields.METHOD, request.getMethod())
                .addKeyValue(AuthAuditFields.REQUEST_PATH, requestPath)
                .log(eventType.code());
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
