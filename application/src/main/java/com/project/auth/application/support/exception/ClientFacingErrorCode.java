package com.project.auth.application.support.exception;

/**
 * 클라이언트에 노출되는 ErrorCode 마커 인터페이스.
 *
 * ApiErrorHttpStatusMapper.map(...)의 시그니처를 이 타입으로 좁혀, 내부 전용인
 * ExternalErrorCode(InfrastructureErrorCode 등)이 클라이언트 응답 경로로 흘러들어가는
 * 것을 컴파일 단계에서 차단한다.
 *
 * TODO(i18n): 현재 message()는 한국어 하드코딩 문자열을 반환한다. MessageSource를 도입할 때는
 * 기존 message()를 기본 메시지 키로 취급하고, 실제 렌더링 문자열은 응답을 만들 때 요청 로케일에
 * 맞추어 MessageSource로 해석하도록 변경한다.
 */
public non-sealed interface ClientFacingErrorCode extends ErrorCode {
}
