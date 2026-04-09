package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.presentation.support.response.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        log.warn("Request body not readable: {}", exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INVALID_REQUEST_BODY))
                .body(ApiResult.failure(
                        CommonErrorCode.INVALID_REQUEST_BODY.code(),
                        CommonErrorCode.INVALID_REQUEST_BODY.message()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        log.warn("Method not supported: {} {}", exception.getMethod(), exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.METHOD_NOT_ALLOWED))
                .body(ApiResult.failure(
                        CommonErrorCode.METHOD_NOT_ALLOWED.code(),
                        CommonErrorCode.METHOD_NOT_ALLOWED.message()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingParameterException(
            MissingServletRequestParameterException exception
    ) {
        log.warn("Missing request parameter: {}", exception.getParameterName());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.MISSING_PARAMETER))
                .body(ApiResult.failure(
                        CommonErrorCode.MISSING_PARAMETER.code(),
                        CommonErrorCode.MISSING_PARAMETER.message()
                ));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResult<Void>> handleTypeMismatchException(
            TypeMismatchException exception
    ) {
        log.warn("Type mismatch for parameter '{}': {}", exception.getPropertyName(), exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INVALID_INPUT))
                .body(ApiResult.failure(
                        CommonErrorCode.INVALID_INPUT.code(),
                        CommonErrorCode.INVALID_INPUT.message()
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception
    ) {
        log.warn("Unsupported media type: {}", exception.getContentType());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE))
                .body(ApiResult.failure(
                        CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.code(),
                        CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.message()
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResult<Void>> handleMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException exception
    ) {
        log.warn("Not acceptable media type: {}", exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.NOT_ACCEPTABLE))
                .body(ApiResult.failure(
                        CommonErrorCode.NOT_ACCEPTABLE.code(),
                        CommonErrorCode.NOT_ACCEPTABLE.message()
                ));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResult<Void>> handleServletRequestBindingException(
            ServletRequestBindingException exception
    ) {
        log.warn("Request binding failed: {}", exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.MISSING_HEADER))
                .body(ApiResult.failure(
                        CommonErrorCode.MISSING_HEADER.code(),
                        CommonErrorCode.MISSING_HEADER.message()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        log.warn("No resource found: {}", exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.RESOURCE_NOT_FOUND))
                .body(ApiResult.failure(
                        CommonErrorCode.RESOURCE_NOT_FOUND.code(),
                        CommonErrorCode.RESOURCE_NOT_FOUND.message()
                ));
    }
}
