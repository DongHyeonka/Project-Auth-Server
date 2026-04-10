package com.project.auth.presentation.support.response;

import org.slf4j.MDC;

import java.time.Instant;

public record ApiResult<T>(
        boolean success,
        String code,
        String message,
        T data,
        String traceId,
        String timestamp
) {

    private static final String TRACE_ID_KEY = "traceId";

    public static <T> ApiResult<T> success(String code, String message, T data) {
        return new ApiResult<>(true, code, message, data, MDC.get(TRACE_ID_KEY), Instant.now().toString());
    }

    public static ApiResult<Void> success(String code, String message) {
        return new ApiResult<>(true, code, message, null, MDC.get(TRACE_ID_KEY), Instant.now().toString());
    }

    public static <T> ApiResult<T> failure(String code, String message, T data) {
        return new ApiResult<>(false, code, message, data, MDC.get(TRACE_ID_KEY), Instant.now().toString());
    }

    public static ApiResult<Void> failure(String code, String message) {
        return new ApiResult<>(false, code, message, null, MDC.get(TRACE_ID_KEY), Instant.now().toString());
    }
}
