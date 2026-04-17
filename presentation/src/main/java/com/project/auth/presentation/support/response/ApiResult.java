package com.project.auth.presentation.support.response;

public record ApiResult<T>(
        boolean success,
        String code,
        String message,
        T data,
        String traceId,
        String timestamp
) {
}
