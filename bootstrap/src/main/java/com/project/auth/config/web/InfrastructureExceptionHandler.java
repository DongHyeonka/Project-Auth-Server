package com.project.auth.config.web;

import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.config.logging.LogSanitizer;
import com.project.auth.infrastructure.support.exception.InfrastructureException;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * InfrastructureErrorCode is kept as an internal classification for logs and alerting.
 * Client responses are intentionally normalized to COMMON-999 to avoid exposing internal dependency details.
 * This advice stays in bootstrap because moving it to presentation would create a
 * presentation -> infrastructure dependency and break the layer rule.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InfrastructureExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureExceptionHandler.class);

    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ApiResult<Void>> handleInfrastructureException(
            InfrastructureException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Infrastructure failure. errorCode={} method={} requestPath={}",
                exception.getErrorCode().code(),
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                exception
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .body(ApiResult.failure(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.message()
                ));
    }
}
