package com.project.auth.config.web;

import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class RequestBoundApiResultFactory implements ApiResultFactory {

    private static final String TRACE_ID_KEY = "traceId";
    /**
     * Sentinel emitted when MDC has no traceId. A literal placeholder is more visible
     * in logs and dashboards than a JSON {@code null}, which would otherwise mask the
     * fact that TraceIdFilter is missing or misconfigured.
     */
    static final String MISSING_TRACE_ID = "-";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final Clock clock;

    public RequestBoundApiResultFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public <T> ApiResult<T> success(String code, String message, T data) {
        return result(true, code, message, data);
    }

    @Override
    public ApiResult<Void> success(String code, String message) {
        return result(true, code, message, null);
    }

    @Override
    public <T> ApiResult<T> failure(String code, String message, T data) {
        return result(false, code, message, data);
    }

    @Override
    public ApiResult<Void> failure(String code, String message) {
        return result(false, code, message, null);
    }

    private <T> ApiResult<T> result(boolean success, String code, String message, T data) {
        String traceId = MDC.get(TRACE_ID_KEY);
        return new ApiResult<>(
                success,
                code,
                message,
                data,
                traceId == null || traceId.isBlank() ? MISSING_TRACE_ID : traceId,
                TIMESTAMP_FORMATTER.format(clock.instant())
        );
    }
}
