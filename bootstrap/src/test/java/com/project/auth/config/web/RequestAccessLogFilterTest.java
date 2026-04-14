package com.project.auth.config.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestAccessLogFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void logsRequestUsingNormalizedRemoteAddrInsteadOfForwardedHeader() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("http.access");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            RequestAccessLogFilter filter = new RequestAccessLogFilter(
                    new AccessLogProperties(List.of("/actuator", "/livez", "/readyz"))
            );

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
            request.setRemoteAddr("10.0.0.7");
            request.addHeader("X-Forwarded-For", "203.0.113.10");
            MockHttpServletResponse response = new MockHttpServletResponse();

            SecurityContextHolder.getContext()
                    .setAuthentication(new TestingAuthenticationToken("alice@example.com", "pw", "ROLE_USER"));
            MDC.put("traceId", "trace-1234");

            filter.doFilter(request, response, new MockFilterChain(new StatusServlet(HttpServletResponse.SC_NO_CONTENT)));

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            String message = event.getFormattedMessage();
            Map<String, Object> keyValues = keyValues(event);
            assertThat(event.getMDCPropertyMap()).containsEntry("traceId", "trace-1234");
            assertThat(message).isEqualTo("ACCESS");
            assertThat(message)
                    .doesNotContain("remoteIp=")
                    .doesNotContain("actorId=")
                    .doesNotContain("status=")
                    .doesNotContain("traceId=");
            assertThat(keyValues)
                    .containsEntry("eventType", "HTTP_ACCESS")
                    .containsEntry("method", "GET")
                    .containsEntry("requestPath", "/api/v1/auth/me")
                    .containsEntry("status", HttpServletResponse.SC_NO_CONTENT)
                    .containsEntry("remoteIp", "10.0.0.0")
                    .containsEntry("actorId", "al***@example.com")
                    .containsEntry("result", "success");
            assertThat(keyValues.get("durationMs")).isInstanceOf(Long.class);
            assertThat((Long) keyValues.get("durationMs")).isGreaterThanOrEqualTo(0L);
            assertThat(keyValues).doesNotContainEntry("remoteIp", "203.0.113.10");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void skipsExcludedPaths() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("http.access");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            RequestAccessLogFilter filter = new RequestAccessLogFilter(
                    new AccessLogProperties(List.of("/actuator", "/livez", "/readyz"))
            );

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            request.setRemoteAddr("127.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain(new StatusServlet(HttpServletResponse.SC_OK)));

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
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

    private static final class StatusServlet extends HttpServlet {

        private final int status;

        private StatusServlet(int status) {
            this.status = status;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setStatus(status);
        }
    }
}
