package com.project.auth.infrastructure.support.exception;

import com.project.auth.application.support.exception.ExternalErrorCode;

/**
 * 내부 전용 인프라 장애 코드. 로그·모니터링·알람 라우팅 분류 용도로만 사용한다.
 * 외부 HTTP 응답에서는 의도적으로 COMMON-999로 정규화되어 노출된다.
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
