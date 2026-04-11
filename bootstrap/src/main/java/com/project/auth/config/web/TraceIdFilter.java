package com.project.auth.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String CLIENT_IP_KEY = "clientIp";
    private static final String USER_AGENT_KEY = "userAgent";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String UNAVAILABLE_VALUE = "-";
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = nextTraceId();
        String clientIp = normalize(request.getRemoteAddr());
        String userAgent = normalize(request.getHeader(USER_AGENT_HEADER));
        response.setHeader(TRACE_ID_HEADER, traceId);

        try (
                MDCCloseable ignoredTraceId = MDC.putCloseable(TRACE_ID_KEY, traceId);
                MDCCloseable ignoredClientIp = MDC.putCloseable(CLIENT_IP_KEY, clientIp);
                MDCCloseable ignoredUserAgent = MDC.putCloseable(USER_AGENT_KEY, userAgent)
        ) {
            filterChain.doFilter(request, response);
        }
    }

    private String nextTraceId() {
        return HEX_FORMAT.toHexDigits(ThreadLocalRandom.current().nextLong());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UNAVAILABLE_VALUE;
        }
        return value;
    }
}
