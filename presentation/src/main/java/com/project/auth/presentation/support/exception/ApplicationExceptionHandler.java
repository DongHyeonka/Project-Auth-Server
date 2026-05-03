package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<Void>> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.AUTHENTICATION_REQUIRED))
                .body(apiResultFactory.failure(
                        AuthErrorCode.AUTHENTICATION_REQUIRED.code(),
                        AuthErrorCode.AUTHENTICATION_REQUIRED.message()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.ACCESS_DENIED))
                .body(apiResultFactory.failure(
                        AuthErrorCode.ACCESS_DENIED.code(),
                        AuthErrorCode.ACCESS_DENIED.message()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Business exception. errorCode={} method={} requestPath={}",
                exception.getErrorCode().code(),
                request.getMethod(),
                LogSanitizer.normalize(request.getRequestURI())
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(exception.getErrorCode()))
                .body(apiResultFactory.failure(exception.getErrorCode().code(), exception.getErrorCode().message()));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ApiResult<Void>> handleMessageNotWritableException(
            HttpMessageNotWritableException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Response body not writable. method={} requestPath={}",
                request.getMethod(),
                LogSanitizer.normalize(request.getRequestURI()),
                exception
        );

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
                LogSanitizer.normalize(request.getRequestURI()),
                exception
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .body(apiResultFactory.failure(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.message()
                ));
    }
}
