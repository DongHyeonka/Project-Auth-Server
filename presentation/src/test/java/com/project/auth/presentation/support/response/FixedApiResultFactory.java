package com.project.auth.presentation.support.response;

public class FixedApiResultFactory implements ApiResultFactory {

    private static final String TRACE_ID = "test-trace-id";
    private static final String TIMESTAMP = "2026-03-14T00:00:00Z";

    @Override
    public <T> ApiResult<T> success(String code, String message, T data) {
        return new ApiResult<>(true, code, message, data, TRACE_ID, TIMESTAMP);
    }

    @Override
    public ApiResult<Void> success(String code, String message) {
        return new ApiResult<>(true, code, message, null, TRACE_ID, TIMESTAMP);
    }

    @Override
    public <T> ApiResult<T> failure(String code, String message, T data) {
        return new ApiResult<>(false, code, message, data, TRACE_ID, TIMESTAMP);
    }

    @Override
    public ApiResult<Void> failure(String code, String message) {
        return new ApiResult<>(false, code, message, null, TRACE_ID, TIMESTAMP);
    }
}
