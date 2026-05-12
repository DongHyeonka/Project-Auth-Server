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
     * default 분기가 존재하는 이유: {@link ClientFacingErrorCode}가 non-sealed이므로
     * (application/presentation 외 모듈도 레이어 경계를 넘지 않고 자체 구현을 추가할 수 있다)
     * 컴파일러가 exhaustiveness를 강제할 수 없다. 새로운 구현이 매핑 테이블에 누락된 채
     * 들어오면 조용히 500이 되는 대신 WARN 로그로 드러내어 운영 대시보드에서 식별 가능하게 한다.
     *
     * "새 ClientFacingErrorCode 구현은 반드시 매핑 테이블에 등록해야 한다"는 아키텍처 차원의
     * 게이트는 ApiErrorHttpStatusMapperClientFacingCoverageTest에 있다.
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
            case KEYCLOAK_ACCOUNT_CONFLICT -> HttpStatus.CONFLICT;
        };
    }
}
