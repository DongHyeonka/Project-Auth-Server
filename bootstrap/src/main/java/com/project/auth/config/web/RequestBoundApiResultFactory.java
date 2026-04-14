package com.project.auth.config.web;

import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class RequestBoundApiResultFactory implements ApiResultFactory {

    private static final String TRACE_ID_KEY = "traceId";

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
        return new ApiResult<>(success, code, message, data, MDC.get(TRACE_ID_KEY), Instant.now(clock).toString());
    }
}
