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
     * 필드와 연결되지 않은 클래스 레벨/cross-field 검증 실패(예: DTO의 {@code @AssertTrue},
     * 커스텀 클래스 레벨 {@code ConstraintValidator})를 담는 버킷 키.
     *
     * 이 버킷이 없으면 ObjectError가 조용히 폐기되어 클라이언트는 {@code errors: {}}만 받고
     * 어디가 잘못됐는지 알 수 없다.
     */
    public static final String GLOBAL_ERROR_KEY = "__global__";

    /**
     * {@code getDefaultMessage()}와 {@code error.code()}가 모두 null/공백인 경우의 폴백 메시지.
     *
     * Bean Validation은 MessageSource로 해석되는 메시지 코드만 정의된 케이스를 허용하며,
     * 이때 getDefaultMessage()는 null을 반환할 수 있다. 이 sentinel이 없으면 응답 errors에
     * {@code [null]} 항목이 그대로 들어간다.
     */
    static final String UNRESOLVED_VIOLATION_MESSAGE = "validation failed";

    private static final Logger log = LoggerFactory.getLogger(ValidationExceptionHandler.class);

    private final ApiResultFactory apiResultFactory;

    public ValidationExceptionHandler(ApiResultFactory apiResultFactory) {
        this.apiResultFactory = apiResultFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldFieldToJsonPointer(fieldError.getField()), k -> new ArrayList<>())
                    .add(resolveMessage(fieldError.getDefaultMessage(), fieldError.getCode()));
        }
        for (ObjectError globalError : exception.getBindingResult().getGlobalErrors()) {
            errors.computeIfAbsent(GLOBAL_ERROR_KEY, k -> new ArrayList<>())
                    .add(resolveMessage(globalError.getDefaultMessage(), globalError.getCode()));
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
    public ResponseEntity<ApiResult<Void>> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String pointer = toJsonPointer(violation.getPropertyPath());
            errors.computeIfAbsent(pointer, k -> new ArrayList<>())
                    .add(resolveMessage(violation.getMessage(), null));
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
    public ResponseEntity<ApiResult<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        int unnamedCounter = 0;
        for (var result : exception.getValueResults()) {
            String paramName = result.getMethodParameter().getParameterName();
            String key;
            if (paramName != null) {
                key = fieldFieldToJsonPointer(paramName);
            } else {
                key = fieldFieldToJsonPointer("unknown_" + result.getMethodParameter().getParameterIndex());
                unnamedCounter++;
            }
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String[] codes = error.getCodes();
                String firstCode = codes != null && codes.length > 0 ? codes[0] : null;
                errors.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(resolveMessage(error.getDefaultMessage(), firstCode));
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
     * Bean Validation의 propertyPath를 JSON Pointer(RFC 6901) 형식으로 변환한다.
     * 예) {@code users[0].email} -> {@code /users/0/email}
     *
     * 서로 다른 파라미터가 우연히 같은 필드명으로 끝나도 충돌이 발생하지 않으며,
     * 클라이언트 입장에서 모호함이 없는 형식이다.
     * 메서드 파라미터 접두사(예: {@code findUser.id})는 출처 파라미터를 식별하므로
     * 첫 pointer 세그먼트로 보존한다.
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

    /**
     * Spring {@code BindingResult.FieldError#getField()} 형식(예: {@code user.email},
     * {@code items[0].name})을 JSON Pointer(RFC 6901)로 변환한다.
     * ConstraintViolationException 핸들러와 키 형식을 통일하여, 응답 errors 맵의 키 규약을
     * 단일화한다(클라이언트가 두 가지 형식을 분기 처리할 필요가 없게 한다).
     */
    private static String fieldFieldToJsonPointer(String field) {
        if (field == null || field.isEmpty()) {
            return "/";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        int length = field.length();
        while (index < length) {
            char ch = field.charAt(index);
            if (ch == '.') {
                index++;
                continue;
            }
            if (ch == '[') {
                int end = field.indexOf(']', index);
                if (end == -1) {
                    builder.append('/').append(escapeJsonPointerSegment(field.substring(index)));
                    break;
                }
                builder.append('/').append(escapeJsonPointerSegment(field.substring(index + 1, end)));
                index = end + 1;
                continue;
            }
            int nextDot = field.indexOf('.', index);
            int nextBracket = field.indexOf('[', index);
            int next = minNonNegative(nextDot, nextBracket);
            if (next == -1) {
                next = length;
            }
            builder.append('/').append(escapeJsonPointerSegment(field.substring(index, next)));
            index = next;
        }
        return builder.length() == 0 ? "/" : builder.toString();
    }

    private static int minNonNegative(int a, int b) {
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static String resolveMessage(String defaultMessage, String fallbackCode) {
        if (defaultMessage != null && !defaultMessage.isBlank()) {
            return defaultMessage;
        }
        if (fallbackCode != null && !fallbackCode.isBlank()) {
            return fallbackCode;
        }
        return UNRESOLVED_VIOLATION_MESSAGE;
    }
}
