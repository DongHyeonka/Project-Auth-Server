package com.project.auth.application.support.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BusinessException 4개 protected 생성자 + getErrorCode 단위 테스트.
 * 다른 모듈(presentation 핸들러)에서 BusinessException을 instantiate해도 모듈 격리 측정에서는
 * application 모듈 라인이 카운트되지 않으므로 본 테스트로 직접 호출.
 */
class BusinessExceptionTest {

    @Test
    void single_arg_constructor_uses_error_code_message() {
        BusinessException ex = new TestException(AuthErrorCode.AUTHENTICATION_REQUIRED);

        assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTHENTICATION_REQUIRED);
        assertThat(ex.getMessage()).isEqualTo(AuthErrorCode.AUTHENTICATION_REQUIRED.message());
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void detail_message_constructor_overrides_message() {
        BusinessException ex = new TestException(AuthErrorCode.ACCESS_DENIED, "specific detail");

        assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_DENIED);
        assertThat(ex.getMessage()).isEqualTo("specific detail");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void cause_constructor_preserves_cause_and_uses_error_code_message() {
        Throwable cause = new IllegalStateException("inner");
        BusinessException ex = new TestException(AuthErrorCode.KEYCLOAK_USER_NOT_FOUND, cause);

        assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.KEYCLOAK_USER_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo(AuthErrorCode.KEYCLOAK_USER_NOT_FOUND.message());
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void detail_message_and_cause_constructor_preserves_both() {
        Throwable cause = new IllegalStateException("inner");
        BusinessException ex = new TestException(AuthErrorCode.KEYCLOAK_CLAIMS_INVALID, "detail", cause);

        assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.KEYCLOAK_CLAIMS_INVALID);
        assertThat(ex.getMessage()).isEqualTo("detail");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    private static final class TestException extends BusinessException {
        TestException(ClientFacingErrorCode errorCode) {
            super(errorCode);
        }
        TestException(ClientFacingErrorCode errorCode, String detailMessage) {
            super(errorCode, detailMessage);
        }
        TestException(ClientFacingErrorCode errorCode, Throwable cause) {
            super(errorCode, cause);
        }
        TestException(ClientFacingErrorCode errorCode, String detailMessage, Throwable cause) {
            super(errorCode, detailMessage, cause);
        }
    }
}
