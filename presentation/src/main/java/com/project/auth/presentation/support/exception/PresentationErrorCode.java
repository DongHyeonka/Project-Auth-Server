package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.ClientFacingErrorCode;

/**
 * 클라이언트에 반환되는 presentation 계층 에러 코드.
 *
 * 클라이언트 SDK가 원인별로 분기 로직을 짤 수 있도록 트리거별로 의도적으로 코드를 분리했다.
 * <ul>
 *   <li>{@link #INVALID_INPUT} — 요청 본문 빈 검증 실패
 *       ({@code @Valid @RequestBody}, MethodArgumentNotValidException).</li>
 *   <li>{@link #INVALID_REQUEST_BODY} — 요청 본문 파싱 실패
 *       (HttpMessageNotReadableException, 예: 잘못된 JSON).</li>
 *   <li>{@link #MISSING_PARAMETER} — 필수 query/form 파라미터 누락
 *       (MissingServletRequestParameterException).</li>
 *   <li>{@link #CONSTRAINT_VIOLATION} — 컨트롤러 메서드 인자 범위 밖에서 트리거된
 *       Jakarta Bean Validation (ConstraintViolationException, 주로 @Validated 서비스
 *       또는 @RequestParam 제약).</li>
 *   <li>{@link #TYPE_MISMATCH} — query/path/form 값을 대상 타입으로 변환 실패
 *       (TypeMismatchException, 예: Long에 {@code id=abc}).</li>
 *   <li>{@link #INVALID_PARAMETER} — 핸들러 메서드 레벨 검증 실패
 *       (HandlerMethodValidationException, 예: {@code @RequestParam @Size}).</li>
 *   <li>{@link #MISSING_HEADER} — 필수 @RequestHeader 누락.</li>
 *   <li>{@link #REQUEST_BINDING_FAILED} — 더 구체적인 코드로 분류되지 않은
 *       일반 ServletRequestBindingException.</li>
 *   <li>{@link #METHOD_NOT_ALLOWED} — 라우트가 지원하지 않는 HTTP 메서드.</li>
 *   <li>{@link #RESOURCE_NOT_FOUND} — 요청 경로에 매칭되는 라우트/정적 리소스 없음.</li>
 *   <li>{@link #UNSUPPORTED_MEDIA_TYPE} — 라우트가 수용하지 않는 Content-Type.</li>
 *   <li>{@link #NOT_ACCEPTABLE} — Accept 헤더를 만족시킬 수 없음.</li>
 *   <li>{@link #PAYLOAD_TOO_LARGE} — multipart 업로드가 허용 한도 초과.</li>
 *   <li>{@link #MESSAGE_NOT_WRITABLE} — 응답 직렬화 실패 (5xx).</li>
 *   <li>{@link #UNHANDLED_CLIENT_ERROR} — 더 구체적인 매핑이 없는 4xx 상태 캐리어
 *       (ResponseStatusException, ErrorResponseException) 또는 컨테이너 레벨 4xx 응답에
 *       대한 폴백.</li>
 * </ul>
 */
public enum PresentationErrorCode implements ClientFacingErrorCode {
    INVALID_INPUT("PRES-001", "요청 값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY("PRES-002", "요청 본문을 읽을 수 없습니다."),
    METHOD_NOT_ALLOWED("PRES-003", "지원하지 않는 HTTP 메서드입니다."),
    MISSING_PARAMETER("PRES-004", "필수 요청 파라미터가 누락되었습니다."),
    RESOURCE_NOT_FOUND("PRES-005", "요청한 리소스를 찾을 수 없습니다."),
    CONSTRAINT_VIOLATION("PRES-006", "요청 값이 제약 조건을 위반했습니다."),
    UNSUPPORTED_MEDIA_TYPE("PRES-007", "지원하지 않는 Content-Type입니다."),
    NOT_ACCEPTABLE("PRES-008", "요청한 응답 형식을 제공할 수 없습니다."),
    MISSING_HEADER("PRES-009", "필수 요청 헤더가 누락되었습니다."),
    REQUEST_BINDING_FAILED("PRES-010", "요청 바인딩에 실패했습니다."),
    MESSAGE_NOT_WRITABLE("PRES-011", "응답 데이터를 처리할 수 없습니다."),
    PAYLOAD_TOO_LARGE("PRES-012", "요청 본문 크기가 허용 한도를 초과했습니다."),
    UNHANDLED_CLIENT_ERROR("PRES-013", "처리하지 못한 클라이언트 오류입니다."),
    TYPE_MISMATCH("PRES-014", "요청 파라미터 타입이 올바르지 않습니다."),
    INVALID_PARAMETER("PRES-015", "요청 파라미터 검증에 실패했습니다.");

    private final String code;
    private final String message;

    PresentationErrorCode(String code, String message) {
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
