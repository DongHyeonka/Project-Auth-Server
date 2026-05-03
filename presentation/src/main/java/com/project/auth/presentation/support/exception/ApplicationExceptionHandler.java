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
     * Honest handling of response-serialization failure.
     *
     * If the response is already committed there is no recovery path: bytes are on
     * the wire and the client will see a truncated response. Returning a body would
     * either be silently dropped or produce a second write that fails the same way.
     * In that case we return an empty ResponseEntity (no body) instead of null,
     * because @ExceptionHandler returning null is interpreted by Spring as "no
     * response written" and can re-enter the resolver chain — exactly the recursive
     * failure we are trying to avoid.
     *
     * If the response is NOT yet committed we attempt the standard ApiResult
     * payload. The original failure may recur if it was structural; the
     * @ExceptionHandler(Exception.class) safety net will catch the second pass.
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
     * Last-resort handler for any exception type not matched by a more specific
     * @ExceptionHandler. Without this, unmatched exceptions bypass advice entirely
     * and fall through to {@code /error}, which returns a generic Spring Boot
     * payload that violates our {@link ApiResult} contract.
     *
     * Always returns 500 + COMMON-999 with a full stack trace at ERROR level so
     * the unknown failure mode is visible to operators.
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
