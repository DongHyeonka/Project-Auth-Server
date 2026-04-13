package com.project.auth.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.project.auth.application.support.audit.AuthAuditEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAuditLoggingConfigurationTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void writesAuditEventFieldsAsStructuredKeyValuesInsteadOfFlattenedMessage() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger("audit.auth");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);

        try {
            MDC.put("traceId", "0123456789abcdef0123456789abcdef");
            AuthAuditLoggingConfiguration.AuthAuditEventLogListener listener =
                    new AuthAuditLoggingConfiguration.AuthAuditEventLogListener();

            listener.onAuthAuditEvent(AuthAuditEvent.warn(
                    "LOGIN_FAILURE",
                    "emailMasked", "al***@example.com",
                    "provider", "LOCAL",
                    "reason", "invalid password"
            ));

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("LOGIN_FAILURE");
            assertThat(event.getFormattedMessage())
                    .doesNotContain("emailMasked=")
                    .doesNotContain("provider=")
                    .doesNotContain("reason=");
            assertThat(event.getMDCPropertyMap())
                    .containsEntry("traceId", "0123456789abcdef0123456789abcdef");
            assertThat(keyValues(event))
                    .containsEntry("eventType", "LOGIN_FAILURE")
                    .containsEntry("emailMasked", "al***@example.com")
                    .containsEntry("provider", "LOCAL")
                    .containsEntry("reason", "invalid_password");
        } finally {
            auditLogger.detachAppender(appender);
        }
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        if (event.getKeyValuePairs() == null) {
            return Map.of();
        }
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(
                        keyValuePair -> keyValuePair.key,
                        keyValuePair -> keyValuePair.value,
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
    }
}
