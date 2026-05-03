package com.project.auth.config.auth.security;

import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.application.support.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityAuditTrailWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditTrailWriter.class);
    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    private final SecurityActorIdResolver actorIdResolver;

    public SecurityAuditTrailWriter(SecurityActorIdResolver actorIdResolver) {
        this.actorIdResolver = actorIdResolver;
    }

    public void record(HttpServletRequest request, AuthAuditEventType eventType, String message) {
        String actorId = actorIdResolver.resolve(request);
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
}
