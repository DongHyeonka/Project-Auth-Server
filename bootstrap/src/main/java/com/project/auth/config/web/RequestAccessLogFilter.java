package com.project.auth.config.web;

import com.project.auth.config.logging.LogSanitizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class RequestAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.access");
    private static final String ACCESS_EVENT_TYPE = "HTTP_ACCESS";

    private final List<String> excludedPathPrefixes;

    public RequestAccessLogFilter(AccessLogProperties accessLogProperties) {
        this.excludedPathPrefixes = accessLogProperties.excludedPathPrefixes() == null
                ? List.of()
                : List.copyOf(accessLogProperties.excludedPathPrefixes());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludedPathPrefixes.stream().anyMatch(path::startsWith);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logRequestSummary(request, response, durationMs);
        }
    }

    private void logRequestSummary(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        String method = request.getMethod();
        String path = LogSanitizer.requestPath(request.getRequestURI());
        int status = response.getStatus();
        String remoteIp = LogSanitizer.clientIp(request.getRemoteAddr());
        String actorId = resolveActorId();
        String result = status < 400 ? "success" : "failure";

        log.atInfo()
                .addKeyValue("eventType", ACCESS_EVENT_TYPE)
                .addKeyValue("method", method)
                .addKeyValue("requestPath", path)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("remoteIp", remoteIp)
                .addKeyValue("actorId", actorId)
                .addKeyValue("result", result)
                .log("ACCESS");
    }

    private String resolveActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return LogSanitizer.actorId(authentication.getName());
        }
        return LogSanitizer.actorId("anonymous");
    }
}
