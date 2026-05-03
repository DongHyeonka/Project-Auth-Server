package com.project.auth.presentation.support.response;

import java.util.List;
import java.util.Map;

/**
 * Unified response envelope for all API responses.
 *
 * - {@code data}: payload for successful responses; null on failure.
 * - {@code errors}: keyed list of validation/cross-field error messages, populated
 *   only by validation failures. Null when there are no field-level diagnostics
 *   to report. Kept separate from {@code data} so OpenAPI does not have to model
 *   {@code data} as a oneOf between domain types and an error map.
 */
public record ApiResult<T>(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, List<String>> errors,
        String traceId,
        String timestamp
) {
}
