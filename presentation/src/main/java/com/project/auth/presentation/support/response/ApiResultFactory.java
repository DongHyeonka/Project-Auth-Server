package com.project.auth.presentation.support.response;

public interface ApiResultFactory {

    <T> ApiResult<T> success(String code, String message, T data);

    ApiResult<Void> success(String code, String message);

    <T> ApiResult<T> failure(String code, String message, T data);

    ApiResult<Void> failure(String code, String message);
}
