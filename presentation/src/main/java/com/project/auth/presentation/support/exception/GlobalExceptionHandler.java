package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.domain.user.exception.DomainException;
import com.project.auth.presentation.support.response.ApiResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Map<String, List<String>>>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }

        log.warn("Validation failed: {}", errors);

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INVALID_INPUT))
                .body(ApiResult.failure(
                        CommonErrorCode.INVALID_INPUT.code(),
                        CommonErrorCode.INVALID_INPUT.message(),
                        errors
                ));
    }

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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Map<String, List<String>>>> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String field = propertyPath.contains(".") ? propertyPath.substring(propertyPath.lastIndexOf('.') + 1) : propertyPath;
            errors.computeIfAbsent(field, k -> new ArrayList<>())
                    .add(violation.getMessage());
        }

        log.warn("Constraint violation: {}", errors);

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.CONSTRAINT_VIOLATION))
                .body(ApiResult.failure(
                        CommonErrorCode.CONSTRAINT_VIOLATION.code(),
                        CommonErrorCode.CONSTRAINT_VIOLATION.message(),
                        errors
                ));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResult<Map<String, List<String>>>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        exception.getValueResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            String field = paramName != null ? paramName : "unknown";
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.computeIfAbsent(field, k -> new ArrayList<>())
                        .add(error.getDefaultMessage());
            }
        });

        log.warn("Handler method validation failed: {}", errors);

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INVALID_INPUT))
                .body(ApiResult.failure(
                        CommonErrorCode.INVALID_INPUT.code(),
                        CommonErrorCode.INVALID_INPUT.message(),
                        errors
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

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResult<Void>> handleDomainException(DomainException exception) {
        log.warn("Unhandled domain exception: {}", exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.DOMAIN_RULE_VIOLATION))
                .body(ApiResult.failure(
                        CommonErrorCode.DOMAIN_RULE_VIOLATION.code(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException exception) {
        log.warn("Business exception [{}]: {}", exception.getErrorCode().code(), exception.getMessage());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(exception.getErrorCode()))
                .body(ApiResult.failure(exception.getErrorCode().code(), exception.getMessage()));
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
}
