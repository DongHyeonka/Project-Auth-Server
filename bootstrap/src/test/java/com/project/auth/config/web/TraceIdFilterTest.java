package com.project.auth.config.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void populatesTraceIdClientIpAndUserAgentIntoMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("User-Agent", "JUnit/5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                new MockFilterChain(new SnapshotServlet(capturedMdc))
        );

        assertThat(capturedMdc.get())
                .containsEntry("clientIp", "10.0.0.7")
                .containsEntry("userAgent", "JUnit/5");
        assertThat(capturedMdc.get().get("traceId")).hasSize(16);
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(capturedMdc.get().get("traceId"));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    private static final class SnapshotServlet extends HttpServlet {

        private final AtomicReference<Map<String, String>> capturedMdc;

        private SnapshotServlet(AtomicReference<Map<String, String>> capturedMdc) {
            this.capturedMdc = capturedMdc;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            capturedMdc.set(MDC.getCopyOfContextMap());
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }
}
