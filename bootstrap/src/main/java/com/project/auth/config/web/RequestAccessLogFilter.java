package com.project.auth.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestAccessLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            logRequestSummary(request, response, durationMs);
        }
    }

    private void logRequestSummary(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();
        String traceId = MDC.get("traceId");
        String remoteIp = resolveRemoteIp(request);
        String principal = resolvePrincipal();
        String result = status < 400 ? "success" : "failure";

        log.info("ACCESS {} {} status={} durationMs={} traceId={} remoteIp={} principal={} result={}",
                method, path, status, durationMs, traceId, remoteIp, principal, result);
    }

    private String resolveRemoteIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolvePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return "anonymous";
    }
}
