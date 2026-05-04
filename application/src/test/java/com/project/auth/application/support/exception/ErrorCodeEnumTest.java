package com.project.auth.application.support.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 1 ErrorCode enum 인스턴스의 code()/message()/values() 호출 라인을 application 모듈
 * 단위 측정에서 카운트되도록 한다. 통상적으로는 다른 모듈이 호출하지만 모듈 격리 측정에서는
 * 그 호출이 application 모듈 .exec에 기록되지 않는다.
 */
class ErrorCodeEnumTest {

    @ParameterizedTest
    @EnumSource(CommonErrorCode.class)
    void common_error_code_exposes_code_and_message(CommonErrorCode code) {
        assertThat(code.code()).isNotBlank().startsWith("COMMON-");
        assertThat(code.message()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(AuthErrorCode.class)
    void auth_error_code_exposes_code_and_message(AuthErrorCode code) {
        assertThat(code.code()).isNotBlank().startsWith("AUTH-");
        assertThat(code.message()).isNotBlank();
    }
}
