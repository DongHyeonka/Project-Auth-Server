package com.project.auth.presentation.support.response;

public record ApiResult<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    public static <T> ApiResult<T> success(String code, String message, T data) {
        return new ApiResult<>(true, code, message, data);
    }

    public static ApiResult<Void> success(String code, String message) {
        return new ApiResult<>(true, code, message, null);
    }

    public static <T> ApiResult<T> failure(String code, String message, T data) {
        return new ApiResult<>(false, code, message, data);
    }

    public static ApiResult<Void> failure(String code, String message) {
        return new ApiResult<>(false, code, message, null);
    }
}
