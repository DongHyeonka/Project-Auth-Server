package com.project.auth.presentation.support.response;

import java.util.List;
import java.util.Map;

public interface ApiResultFactory {

    <T> ApiResult<T> success(String code, String message, T data);

    ApiResult<Void> success(String code, String message);

    ApiResult<Void> failure(String code, String message);

    /**
     * Failure with validation/cross-field diagnostics. Stored in the dedicated
     * {@code errors} field so the response shape stays consistent and clients can
     * deserialize {@code data} as the success type without needing to model an
     * error union.
     */
    ApiResult<Void> failure(String code, String message, Map<String, List<String>> errors);
}
