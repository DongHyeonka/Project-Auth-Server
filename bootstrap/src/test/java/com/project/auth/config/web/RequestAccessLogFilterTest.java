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
import java.util.List;

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
            assertThat(event.getMDCPropertyMap()).containsEntry("traceId", "trace-1234");
            assertThat(message).contains("remoteIp=10.0.0.7");
            assertThat(message).doesNotContain("203.0.113.10");
            assertThat(message).contains("principal=alice@example.com");
            assertThat(message).contains("status=204");
            assertThat(message).doesNotContain("traceId=");
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
