package com.project.auth.config.web;

import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RequestBoundApiResultFactory implements ApiResultFactory {

    private static final String TRACE_ID_KEY = "traceId";
    /**
     * MDC에 traceId가 없을 때 사용하는 sentinel. JSON {@code null}로 그대로 두면
     * TraceIdFilter가 누락/오설정된 사실이 가려지므로, 명시적인 placeholder 문자열로
     * 로그·대시보드에서 즉시 식별 가능하게 한다.
     */
    static final String MISSING_TRACE_ID = "-";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final Clock clock;

    public RequestBoundApiResultFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public <T> ApiResult<T> success(String code, String message, T data) {
        return result(true, code, message, data, null);
    }

    @Override
    public ApiResult<Void> success(String code, String message) {
        return result(true, code, message, null, null);
    }

    @Override
    public ApiResult<Void> failure(String code, String message) {
        return result(false, code, message, null, null);
    }

    @Override
    public ApiResult<Void> failure(String code, String message, Map<String, List<String>> errors) {
        return result(false, code, message, null, errors);
    }

    private <T> ApiResult<T> result(
            boolean success,
            String code,
            String message,
            T data,
            Map<String, List<String>> errors
    ) {
        String traceId = MDC.get(TRACE_ID_KEY);
        return new ApiResult<>(
                success,
                code,
                message,
                data,
                errors,
                traceId == null || traceId.isBlank() ? MISSING_TRACE_ID : traceId,
                TIMESTAMP_FORMATTER.format(clock.instant())
        );
    }
}
