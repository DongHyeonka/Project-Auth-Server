package com.project.auth.presentation.support.exception;

import com.project.auth.application.auth.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.domain.user.exception.DomainException;
import com.project.auth.presentation.support.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.security.Principal;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApplicationExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String ANONYMOUS_PRINCIPAL = "anonymous";

    private static final Logger log = LoggerFactory.getLogger(ApplicationExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResult<Void>> handleDomainException(DomainException exception) {
        log.warn("Unhandled domain exception exposed to client: {}", exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.DOMAIN_RULE_VIOLATION))
                .body(ApiResult.failure(
                        CommonErrorCode.DOMAIN_RULE_VIOLATION.code(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAuthorizationDeniedException(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Access denied [traceId={}] [principal={}] for {} {}: {}",
                MDC.get(TRACE_ID_KEY),
                resolvePrincipal(request),
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.ACCESS_DENIED))
                .body(ApiResult.failure(
                        AuthErrorCode.ACCESS_DENIED.code(),
                        AuthErrorCode.ACCESS_DENIED.message()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException exception) {
        log.warn("Business exception [{}]: {}", exception.getErrorCode().code(), exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(exception.getErrorCode()))
                .body(ApiResult.failure(exception.getErrorCode().code(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ApiResult<Void>> handleMessageNotWritableException(
            HttpMessageNotWritableException exception
    ) {
        log.error("Response body not writable: {}", exception.getMessage(), exception);

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.MESSAGE_NOT_WRITABLE))
                .body(ApiResult.failure(
                        CommonErrorCode.MESSAGE_NOT_WRITABLE.code(),
                        CommonErrorCode.MESSAGE_NOT_WRITABLE.message()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception", exception);

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .body(ApiResult.failure(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.message()
                ));
    }

    private String resolvePrincipal(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return principal.getName();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }

        return ANONYMOUS_PRINCIPAL;
    }
}
