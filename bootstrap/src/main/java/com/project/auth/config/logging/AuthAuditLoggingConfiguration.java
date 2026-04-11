package com.project.auth.config.logging;

import com.project.auth.application.support.audit.AuditLevel;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.stream.Collectors;

@Configuration
public class AuthAuditLoggingConfiguration {

    private static final Logger audit = LoggerFactory.getLogger("audit.auth");

    @Bean
    public AuthAuditEventPublisher authAuditEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return applicationEventPublisher::publishEvent;
    }

    @Bean
    public AuthAuditEventLogListener authAuditEventListener() {
        return new AuthAuditEventLogListener();
    }

    static final class AuthAuditEventLogListener {

        @EventListener
        public void onAuthAuditEvent(AuthAuditEvent event) {
            String message = format(event);
            if (event.level() == AuditLevel.WARN) {
                audit.warn(message);
                return;
            }
            audit.info(message);
        }
    }

    private static String format(AuthAuditEvent event) {
        if (event.fields().isEmpty()) {
            return event.eventType();
        }

        String fieldExpression = event.fields().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(" "));

        return event.eventType() + " " + fieldExpression;
    }
}
