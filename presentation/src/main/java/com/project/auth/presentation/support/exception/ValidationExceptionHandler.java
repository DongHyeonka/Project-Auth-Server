package com.project.auth.presentation.support.exception;

import com.project.auth.presentation.support.response.ApiResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ValidationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidationExceptionHandler.class);

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

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_INPUT))
                .body(ApiResult.failure(
                        PresentationErrorCode.INVALID_INPUT.code(),
                        PresentationErrorCode.INVALID_INPUT.message(),
                        errors
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

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.CONSTRAINT_VIOLATION))
                .body(ApiResult.failure(
                        PresentationErrorCode.CONSTRAINT_VIOLATION.code(),
                        PresentationErrorCode.CONSTRAINT_VIOLATION.message(),
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

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_INPUT))
                .body(ApiResult.failure(
                        PresentationErrorCode.INVALID_INPUT.code(),
                        PresentationErrorCode.INVALID_INPUT.message(),
                        errors
                ));
    }
}
