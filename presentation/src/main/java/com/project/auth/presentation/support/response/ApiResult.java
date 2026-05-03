package com.project.auth.presentation.support.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 모든 API 응답의 통일된 응답 봉투(envelope).
 *
 * <ul>
 *   <li>{@code data}: 성공 응답의 페이로드. 실패 시에는 null.</li>
 *   <li>{@code errors}: 검증/cross-field 에러 메시지의 키-리스트 맵. 검증 실패에서만
 *       채워진다. 필드 레벨 진단이 없으면 null. {@code data}와 분리해서 OpenAPI 스펙이
 *       {@code data}를 도메인 타입과 에러 맵의 oneOf로 모델링할 필요가 없게 한다.</li>
 * </ul>
 *
 * null 필드는 JSON 직렬화 시 제외하여, 성공 응답에 {@code "errors": null} 잡음이나
 * 실패 응답에 {@code "data": null}이 따라붙지 않도록 한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(
        boolean success,
        String code,
        String message,
        T data,
        Map<String, List<String>> errors,
        String traceId,
        String timestamp
) {
}
