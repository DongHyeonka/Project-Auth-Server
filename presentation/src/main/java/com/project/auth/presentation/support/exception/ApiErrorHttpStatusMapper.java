package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.ClientFacingErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public final class ApiErrorHttpStatusMapper {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHttpStatusMapper.class);

    private ApiErrorHttpStatusMapper() {
    }

    /**
     * The default branch exists because {@link ClientFacingErrorCode} is non-sealed
     * (modules outside application/presentation may add their own implementations
     * without crossing the layer boundary). When that happens the compiler cannot
     * enforce exhaustiveness, so we log a WARN to surface the gap in operator
     * dashboards instead of silently 500-ing forever. The architecture-level
     * gate that any new ClientFacingErrorCode must be added to the mapping table
     * lives in ApiErrorHttpStatusMapperClientFacingCoverageTest.
     */
    public static HttpStatus map(ClientFacingErrorCode errorCode) {
        return switch (errorCode) {
            case CommonErrorCode commonErrorCode -> mapCommon(commonErrorCode);
            case AuthErrorCode authErrorCode -> mapAuth(authErrorCode);
            case PresentationErrorCode presentationErrorCode -> mapPresentation(presentationErrorCode);
            default -> {
                log.warn("Unmapped ClientFacingErrorCode reached default branch. type={} code={} -> falling back to 500",
                        errorCode.getClass().getName(), errorCode.code());
                yield HttpStatus.INTERNAL_SERVER_ERROR;
            }
        };
    }

    private static HttpStatus mapCommon(CommonErrorCode errorCode) {
        return switch (errorCode) {
            case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static HttpStatus mapPresentation(PresentationErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_INPUT, INVALID_REQUEST_BODY, MISSING_PARAMETER, CONSTRAINT_VIOLATION, MISSING_HEADER, REQUEST_BINDING_FAILED, TYPE_MISMATCH, INVALID_PARAMETER -> HttpStatus.BAD_REQUEST;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case NOT_ACCEPTABLE -> HttpStatus.NOT_ACCEPTABLE;
            case PAYLOAD_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case MESSAGE_NOT_WRITABLE -> HttpStatus.INTERNAL_SERVER_ERROR;
            case UNHANDLED_CLIENT_ERROR -> HttpStatus.BAD_REQUEST;
        };
    }

    private static HttpStatus mapAuth(AuthErrorCode errorCode) {
        return switch (errorCode) {
            case AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case KEYCLOAK_CLAIMS_INVALID -> HttpStatus.BAD_REQUEST;
            case KEYCLOAK_USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
    }
}
