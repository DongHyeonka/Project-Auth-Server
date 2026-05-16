package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApplicationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationExceptionHandler.class);

    private final ApiResultFactory apiResultFactory;

    public ApplicationExceptionHandler(ApiResultFactory apiResultFactory) {
        this.apiResultFactory = apiResultFactory;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Business exception. exceptionType={} errorCode={} method={} requestPath={}",
                exception.getClass().getSimpleName(),
                exception.getErrorCode().code(),
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI())
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(exception.getErrorCode()))
                .body(apiResultFactory.failure(exception.getErrorCode().code(), exception.getErrorCode().message()));
    }

    /**
     * 응답 직렬화 실패에 대한 정직한 처리.
     *
     * 응답이 이미 커밋된 상태(이미 바이트가 wire에 나간 상태)라면 회복 경로가 없다.
     * 같은 직렬화 경로로 본문을 또 쓰려고 하면 동일한 실패가 재발하거나 무시된다.
     * 이때는 본문 없는 빈 ResponseEntity(500)를 반환한다 — @ExceptionHandler가 null을
     * 반환하면 Spring은 "응답이 작성되지 않음"으로 해석해 리졸버 체인으로 재진입할 수 있고,
     * 우리가 막으려는 재귀 실패가 바로 그것이다.
     *
     * 응답이 아직 커밋되지 않았다면 표준 ApiResult 본문을 시도한다. 직렬화 실패가
     * 구조적인 원인이면 2차 시도도 같은 이유로 실패할 수 있는데, 그때는
     * @ExceptionHandler(Exception.class) 안전망이 다시 잡아낸다.
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ApiResult<Void>> handleMessageNotWritableException(
            HttpMessageNotWritableException exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.error(
                "Response body not writable. committed={} method={} requestPath={}",
                response.isCommitted(),
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                exception
        );

        if (response.isCommitted()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.MESSAGE_NOT_WRITABLE))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.MESSAGE_NOT_WRITABLE.code(),
                        PresentationErrorCode.MESSAGE_NOT_WRITABLE.message()
                ));
    }

    /**
     * 더 구체적인 @ExceptionHandler에 매칭되지 않은 모든 예외에 대한 최후의 안전망.
     *
     * 이 핸들러가 없으면 매칭되지 않은 예외는 advice를 우회해 {@code /error}로 흘러가고,
     * Spring Boot 기본 응답 형태가 반환되어 {@link ApiResult} 계약이 깨진다.
     *
     * 항상 500 + COMMON-999를 반환하고, 알 수 없는 장애 모드를 운영이 식별할 수 있도록
     * ERROR 레벨로 전체 스택트레이스를 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUncaughtException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Uncaught exception reached @ExceptionHandler safety net. exceptionType={} method={} requestPath={}",
                exception.getClass().getName(),
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                exception
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .body(apiResultFactory.failure(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.message()
                ));
    }
}
