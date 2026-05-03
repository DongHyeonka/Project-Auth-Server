package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.ClientFacingErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiErrorHttpStatusMapper 속성 기반 테스트.
 *
 * 검증 속성:
 * - 모든 ClientFacingErrorCode enum 값에 대해 결과는 null이 아니다.
 * - AUTHENTICATION_REQUIRED는 항상 401, ACCESS_DENIED는 항상 403.
 * - 모든 결과 status는 4xx 또는 5xx (2xx/3xx로 분류되지 않는다).
 * - 같은 입력은 항상 같은 출력 (참조 투명성).
 */
class ApiErrorHttpStatusMapperPropertyTest {

    @Property
    void map_never_returns_null_for_any_known_enum(@ForAll("anyClientFacing") ClientFacingErrorCode code) {
        assertThat(ApiErrorHttpStatusMapper.map(code)).isNotNull();
    }

    @Property
    void map_result_is_always_4xx_or_5xx(@ForAll("anyClientFacing") ClientFacingErrorCode code) {
        HttpStatus status = ApiErrorHttpStatusMapper.map(code);
        assertThat(status.is4xxClientError() || status.is5xxServerError())
                .as("ErrorCode %s mapped to non-error status %s", code, status)
                .isTrue();
    }

    @Property
    void map_is_referentially_transparent(@ForAll("anyClientFacing") ClientFacingErrorCode code) {
        assertThat(ApiErrorHttpStatusMapper.map(code))
                .isEqualTo(ApiErrorHttpStatusMapper.map(code));
    }

    @Property
    void authentication_required_is_always_401(@ForAll("authReq") AuthErrorCode code) {
        assertThat(ApiErrorHttpStatusMapper.map(code)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Property
    void access_denied_is_always_403(@ForAll("accessDenied") AuthErrorCode code) {
        assertThat(ApiErrorHttpStatusMapper.map(code)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Provide
    Arbitrary<ClientFacingErrorCode> anyClientFacing() {
        Arbitrary<ClientFacingErrorCode> common = Arbitraries.of(CommonErrorCode.values());
        Arbitrary<ClientFacingErrorCode> auth = Arbitraries.of(AuthErrorCode.values());
        Arbitrary<ClientFacingErrorCode> presentation = Arbitraries.of(PresentationErrorCode.values());
        return Arbitraries.oneOf(common, auth, presentation);
    }

    @Provide
    Arbitrary<AuthErrorCode> authReq() {
        return Arbitraries.just(AuthErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Provide
    Arbitrary<AuthErrorCode> accessDenied() {
        return Arbitraries.just(AuthErrorCode.ACCESS_DENIED);
    }
}
