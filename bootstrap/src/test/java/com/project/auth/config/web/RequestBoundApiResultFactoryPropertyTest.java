package com.project.auth.config.web;

import com.project.auth.presentation.support.response.ApiResult;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.AfterTry;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestBoundApiResultFactory 속성 기반 테스트.
 *
 * 검증 속성:
 * - 모든 응답에 traceId가 비어 있지 않다.
 * - MDC에 traceId가 비어 있으면 sentinel "-"로 채워진다.
 * - MDC에 traceId가 있으면 그 값을 그대로 사용한다.
 * - timestamp는 ISO_INSTANT 형식으로 항상 채워진다.
 * - success/failure 플래그는 메서드와 일치한다.
 * - data와 errors는 호출한 메서드 시그니처에 맞게 들어간다 (성공은 errors=null, 실패의 일부는 data=null).
 */
class RequestBoundApiResultFactoryPropertyTest {

    private static final String TRACE_ID_KEY = "traceId";
    private final RequestBoundApiResultFactory factory =
            new RequestBoundApiResultFactory(Clock.fixed(Instant.parse("2026-05-03T00:00:00Z"), ZoneOffset.UTC));

    @AfterTry
    void clearMdc() {
        MDC.clear();
    }

    @Property
    void success_with_data_carries_data_and_no_errors(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String code,
            @ForAll @AlphaChars @StringLength(min = 0, max = 50) String message,
            @ForAll @AlphaChars @StringLength(min = 0, max = 50) String payload
    ) {
        ApiResult<String> result = factory.success(code, message, payload);
        assertThat(result.success()).isTrue();
        assertThat(result.code()).isEqualTo(code);
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.data()).isEqualTo(payload);
        assertThat(result.errors()).isNull();
    }

    @Property
    void failure_without_data_carries_no_data_and_no_errors(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String code,
            @ForAll @AlphaChars @StringLength(min = 0, max = 50) String message
    ) {
        ApiResult<Void> result = factory.failure(code, message);
        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.errors()).isNull();
    }

    @Property
    void traceId_falls_back_to_dash_when_mdc_is_empty(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String code
    ) {
        MDC.remove(TRACE_ID_KEY);
        ApiResult<Void> result = factory.failure(code, "irrelevant");
        assertThat(result.traceId()).isEqualTo("-");
    }

    @Property
    void traceId_uses_mdc_value_when_present(
            @ForAll("nonBlankAscii") String mdcTraceId,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String code
    ) {
        MDC.put(TRACE_ID_KEY, mdcTraceId);
        ApiResult<Void> result = factory.failure(code, "irrelevant");
        assertThat(result.traceId()).isEqualTo(mdcTraceId);
    }

    @Property
    void traceId_falls_back_to_dash_for_blank_mdc_value(
            @ForAll("blankString") String blankMdc,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String code
    ) {
        MDC.put(TRACE_ID_KEY, blankMdc);
        ApiResult<Void> result = factory.failure(code, "irrelevant");
        assertThat(result.traceId()).isEqualTo("-");
    }

    @Property
    void timestamp_is_always_iso_instant_form(@ForAll @AlphaChars @StringLength(min = 1, max = 20) String code) {
        ApiResult<Void> result = factory.failure(code, "irrelevant");
        assertThat(result.timestamp()).isEqualTo("2026-05-03T00:00:00Z");
    }

    @Provide
    Arbitrary<String> nonBlankAscii() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(40);
    }

    @Provide
    Arbitrary<String> blankString() {
        return Arbitraries.of("", " ", "\t", "  ");
    }
}
