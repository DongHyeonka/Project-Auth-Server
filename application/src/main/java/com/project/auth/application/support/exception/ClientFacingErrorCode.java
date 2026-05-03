package com.project.auth.application.support.exception;

/**
 * Marker for ErrorCode values that may be returned to API clients. Used to narrow
 * ApiErrorHttpStatusMapper.map(...) so internal-only ExternalErrorCode subtypes
 * (e.g., InfrastructureErrorCode) cannot reach client-facing code paths.
 *
 * TODO(i18n): message() currently returns a hard-coded Korean string. When MessageSource
 * is introduced, treat the existing message() as the default key and resolve the
 * actual rendered text against the request locale at the response-rendering layer.
 */
public non-sealed interface ClientFacingErrorCode extends ErrorCode {
}
