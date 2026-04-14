package com.project.auth.config.auth.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginFailureHandlerTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void warnLogDoesNotCarryStackTraceButAuditLogKeepsTraceId() throws Exception {
        Logger classLogger = (Logger) LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);
        Logger auditLogger = (Logger) LoggerFactory.getLogger("audit.auth");
        ListAppender<ILoggingEvent> classAppender = new ListAppender<>();
        ListAppender<ILoggingEvent> auditAppender = new ListAppender<>();
        classAppender.start();
        auditAppender.start();
        classLogger.addAppender(classAppender);
        auditLogger.addAppender(auditAppender);

        try {
            OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(new ObjectMapper());
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak-google");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MDC.put("traceId", "trace-oauth-1");
            MDC.put("clientIp", "10.0.0.0");
            MDC.put("userAgent", "JUnit/5");

            handler.onAuthenticationFailure(
                    request,
                    response,
                    new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "provider rejected authentication")
            );

            assertThat(classAppender.list).hasSize(1);
            ILoggingEvent warnEvent = classAppender.list.getFirst();
            assertThat(warnEvent.getLevel()).isEqualTo(Level.WARN);
            assertThat(warnEvent.getThrowableProxy()).isNull();
            assertThat(warnEvent.getFormattedMessage())
                    .contains("reason=access_denied")
                    .doesNotContain("provider rejected authentication");

            assertThat(auditAppender.list).hasSize(1);
            ILoggingEvent auditEvent = auditAppender.list.getFirst();
            assertThat(auditEvent.getFormattedMessage())
                    .contains("OAUTH_AUTHENTICATION_FAILURE")
                    .doesNotContain("reason=")
                    .doesNotContain("method=")
                    .doesNotContain("traceId=");
            assertThat(keyValues(auditEvent))
                    .containsEntry("eventType", "OAUTH_AUTHENTICATION_FAILURE")
                    .containsEntry("method", "GET")
                    .containsEntry("requestPath", "/login/oauth2/code/keycloak-google")
                    .containsEntry("reason", "access_denied");
            assertThat(auditEvent.getMDCPropertyMap())
                    .containsEntry("traceId", "trace-oauth-1")
                    .containsEntry("clientIp", "10.0.0.0")
                    .containsEntry("userAgent", "JUnit/5");
        } finally {
            classLogger.detachAppender(classAppender);
            auditLogger.detachAppender(auditAppender);
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
