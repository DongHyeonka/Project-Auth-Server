package com.project.auth.application.support.audit;

public interface AuthAuditEventPublisher {

    void publish(AuthAuditEvent event);
}
