package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.ClientFacingErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiErrorHttpStatusMapper의 non-sealed default 분기를 presentation 모듈 단위 측정에서
 * 카운트되도록 직접 트리거. (동일 검증이 bootstrap의 ArchUnit 가드 테스트에도 있지만
 * 그쪽은 모듈 경계로 인해 presentation 측정에 안 잡힌다.)
 */
class ApiErrorHttpStatusMapperDefaultBranchTest {

    @Test
    void unknown_client_facing_implementation_falls_back_to_500() {
        ClientFacingErrorCode unknown = new ClientFacingErrorCode() {
            @Override public String code() { return "UNKNOWN-001"; }
            @Override public String message() { return "unknown"; }
        };
        assertThat(ApiErrorHttpStatusMapper.map(unknown)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
