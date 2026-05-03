package com.project.auth.infrastructure.support.exception;

/**
 * 인프라 계층(DB, 외부 HTTP 클라이언트, 시크릿 저장소 등) 장애를 표현하는 내부 전용 예외.
 *
 * 등록된 @ExceptionHandler는 ERROR 레벨로 전체 스택트레이스와 함께 {@code detailMessage}를
 * 로깅한다. 따라서 어댑터는 {@code detailMessage}에 민감 정보를 절대 포함시키면 안 된다.
 *
 * detailMessage에 금지: 커넥션 문자열, DB 자격 증명, Vault 토큰, JWT 페이로드 내용,
 * 정제되지 않은 사용자 입력, 외부 호출의 전체 요청/응답 바디.
 *
 * detailMessage에 허용: 식별 불가 형태의 상관 ID, 호스트/서비스 이름, 정제된 상태 코드,
 * 장애 모드를 설명하는 일반 문장. 이 detail은 운영자 트리아지용이며, 클라이언트는 항상
 * COMMON-999만 받는다.
 */
public class InfrastructureException extends RuntimeException {

    private final InfrastructureErrorCode errorCode;

    public InfrastructureException(InfrastructureErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public InfrastructureException(InfrastructureErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public InfrastructureException(InfrastructureErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public InfrastructureException(InfrastructureErrorCode errorCode, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        this.errorCode = errorCode;
    }

    public InfrastructureErrorCode getErrorCode() {
        return errorCode;
    }
}
