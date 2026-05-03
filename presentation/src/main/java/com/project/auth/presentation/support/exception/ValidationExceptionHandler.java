package com.project.auth.presentation.support.exception;

import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
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

    /**
     * Bucket key used for class-level / cross-field validation failures that have
     * no field association (e.g., {@code @AssertTrue} on the DTO, custom class-level
     * {@code ConstraintValidator}). Without this bucket, ObjectError instances would
     * be silently dropped and clients would see {@code errors: {}} with no diagnosis.
     */
    public static final String GLOBAL_ERROR_KEY = "__global__";

    private static final Logger log = LoggerFactory.getLogger(ValidationExceptionHandler.class);

    private final ApiResultFactory apiResultFactory;

    public ValidationExceptionHandler(ApiResultFactory apiResultFactory) {
        this.apiResultFactory = apiResultFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Map<String, List<String>>>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }
        for (ObjectError globalError : exception.getBindingResult().getGlobalErrors()) {
            errors.computeIfAbsent(GLOBAL_ERROR_KEY, k -> new ArrayList<>())
                    .add(globalError.getDefaultMessage());
        }

        log.warn("Validation failed: {}", errors);

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_INPUT))
                .body(apiResultFactory.failure(
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
            String pointer = toJsonPointer(violation.getPropertyPath());
            errors.computeIfAbsent(pointer, k -> new ArrayList<>())
                    .add(violation.getMessage());
        }

        log.warn("Constraint violation: {}", errors);

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.CONSTRAINT_VIOLATION))
                .body(apiResultFactory.failure(
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
        int unnamedCounter = 0;
        for (var result : exception.getValueResults()) {
            String paramName = result.getMethodParameter().getParameterName();
            String key;
            if (paramName != null) {
                key = paramName;
            } else {
                key = "unknown_" + result.getMethodParameter().getParameterIndex();
                unnamedCounter++;
            }
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(error.getDefaultMessage());
            }
        }
        if (unnamedCounter > 0) {
            log.warn("Handler method validation: {} unnamed parameter(s); compile with -parameters to recover names",
                    unnamedCounter);
        }

        log.warn("Handler method validation failed: {}", errors);

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_PARAMETER))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.INVALID_PARAMETER.code(),
                        PresentationErrorCode.INVALID_PARAMETER.message(),
                        errors
                ));
    }

    /**
     * Converts a Bean Validation property path into a JSON Pointer (RFC 6901),
     * e.g., {@code users[0].email} -> {@code /users/0/email}, so collisions between
     * different parameters that happen to end in the same field name are impossible
     * and the format is unambiguous for clients.
     *
     * Method parameter prefixes (e.g., {@code findUser.id}) are preserved as the
     * first pointer segment because they identify the source parameter.
     */
    private static String toJsonPointer(Path path) {
        StringBuilder builder = new StringBuilder();
        for (Path.Node node : path) {
            String name = node.getName();
            if (name == null) {
                continue;
            }
            builder.append('/').append(escapeJsonPointerSegment(name));
            Integer index = node.getIndex();
            if (index != null) {
                builder.append('/').append(index);
            } else if (node.getKey() != null) {
                builder.append('/').append(escapeJsonPointerSegment(String.valueOf(node.getKey())));
            }
        }
        return builder.length() == 0 ? "/" : builder.toString();
    }

    private static String escapeJsonPointerSegment(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
