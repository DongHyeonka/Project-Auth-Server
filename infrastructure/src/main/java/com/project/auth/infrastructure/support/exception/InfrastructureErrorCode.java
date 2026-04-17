package com.project.auth.infrastructure.support.exception;

import com.project.auth.application.support.exception.ExternalErrorCode;

/**
 * Internal-only infrastructure failure codes used for logs, monitoring, and alert routing.
 * External HTTP responses intentionally collapse these failures to COMMON-999.
 */
public enum InfrastructureErrorCode implements ExternalErrorCode {
    PERSISTED_DATA_INVALID("INFRA-003", "저장된 데이터가 도메인 규칙에 맞지 않습니다."),
    EXTERNAL_SERVICE_ERROR("INFRA-999", "외부 시스템 연동 중 오류가 발생했습니다.");

    private final String code;
    private final String message;

    InfrastructureErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
