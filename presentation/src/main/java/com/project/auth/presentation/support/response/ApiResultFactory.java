package com.project.auth.presentation.support.response;

import java.util.List;
import java.util.Map;

public interface ApiResultFactory {

    <T> ApiResult<T> success(String code, String message, T data);

    ApiResult<Void> success(String code, String message);

    ApiResult<Void> failure(String code, String message);

    /**
     * 검증/cross-field 진단을 동반한 실패 응답. 진단 정보는 전용 {@code errors} 필드에
     * 저장되므로 응답 형태가 일관되며, 클라이언트는 에러 union을 모델링하지 않고도
     * {@code data}를 성공 타입으로 그대로 역직렬화할 수 있다.
     */
    ApiResult<Void> failure(String code, String message, Map<String, List<String>> errors);
}
