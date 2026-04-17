package com.project.auth.config.logging;

import com.project.auth.application.support.audit.AuditLevel;
import com.project.auth.application.support.audit.AuthAuditEvent;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.support.audit.AuthAuditFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

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
            LoggingEventBuilder builder = event.level() == AuditLevel.WARN ? audit.atWarn() : audit.atInfo();
            builder.addKeyValue(AuthAuditFields.EVENT_TYPE, event.eventType());
            event.fields().forEach((key, value) -> builder.addKeyValue(key, LogSanitizer.normalize(value)));
            builder.log(event.eventType());
        }
    }
}
