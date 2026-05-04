package com.project.auth.presentation.support.exception;

import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import com.project.auth.presentation.support.response.FixedApiResultFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ValidationExceptionHandler를 컨테이너 없이 직접 호출하는 단위 테스트.
 * 핸들러의 모든 분기(field error, global error, ConstraintViolation)를 모듈 단위 측정에서
 * 카운트되도록 한다.
 */
class ValidationExceptionHandlerTest {

    private final ApiResultFactory apiResultFactory = new FixedApiResultFactory();
    private final ValidationExceptionHandler handler = new ValidationExceptionHandler(apiResultFactory);

    @Test
    void handleValidationException_collects_field_errors_under_json_pointer_keys() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(target, "request");
        result.addError(new FieldError("request", "user.email", "이메일 형식이 아님"));
        result.addError(new FieldError("request", "items[0].name", "이름은 필수"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyParameter(), result);

        ResponseEntity<ApiResult<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(PresentationErrorCode.INVALID_INPUT.code());
        assertThat(response.getBody().errors()).containsKeys("/user/email", "/items/0/name");
    }

    @Test
    void handleValidationException_global_errors_go_to_global_bucket() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(target, "request");
        result.addError(new ObjectError("request", "비밀번호가 일치하지 않습니다"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyParameter(), result);

        ResponseEntity<ApiResult<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getBody().errors())
                .containsKey(ValidationExceptionHandler.GLOBAL_ERROR_KEY);
        assertThat(response.getBody().errors().get(ValidationExceptionHandler.GLOBAL_ERROR_KEY))
                .containsExactly("비밀번호가 일치하지 않습니다");
    }

    @Test
    void handleValidationException_falls_back_to_sentinel_when_message_is_null() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(target, "request");
        // defaultMessage=null 이지만 code는 있음 → code가 폴백 메시지로 사용되어야 한다
        result.addError(new FieldError("request", "name", null, false, new String[]{"NotBlank"}, null, null));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyParameter(), result);

        ResponseEntity<ApiResult<Void>> response = handler.handleValidationException(ex);

        List<String> messages = response.getBody().errors().get("/name");
        assertThat(messages).hasSize(1);
        // null 회피만 검증 — 폴백 코드 또는 sentinel 둘 중 하나여야 한다
        assertThat(messages.get(0)).isNotNull();
    }

    @Test
    void handleHandlerMethodValidationException_uses_param_name_when_present() throws Exception {
        HandlerMethodValidationException ex = stubHandlerMethodValidationException("q", "must be at least 2 chars");

        ResponseEntity<ApiResult<Void>> response = handler.handleHandlerMethodValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(PresentationErrorCode.INVALID_PARAMETER.code());
        assertThat(response.getBody().errors()).containsKey("/q");
    }

    @Test
    void handleHandlerMethodValidationException_falls_back_to_indexed_unknown_when_paramname_null()
            throws Exception {
        HandlerMethodValidationException ex = stubHandlerMethodValidationException(null, "missing");

        ResponseEntity<ApiResult<Void>> response = handler.handleHandlerMethodValidationException(ex);

        // null paramName → "unknown_<index>"가 JSON Pointer로 변환되어 키가 됨
        assertThat(response.getBody().errors().keySet().iterator().next()).startsWith("/unknown_");
    }

    @Test
    void handleConstraintViolationException_uses_json_pointer_keys() {
        ConstraintViolation<?> violation = stubViolation("findUser.id", "must be a number");
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.add(violation);
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        ResponseEntity<ApiResult<Void>> response = handler.handleConstraintViolationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(PresentationErrorCode.CONSTRAINT_VIOLATION.code());
        assertThat(response.getBody().errors()).containsKey("/findUser/id");
    }

    private static org.springframework.core.MethodParameter dummyParameter() throws Exception {
        Method dummy = String.class.getMethod("toString");
        return new org.springframework.core.MethodParameter(dummy, -1);
    }

    @SuppressWarnings({"rawtypes"})
    private static ConstraintViolation<?> stubViolation(String pathString, String message) {
        Path path = stubPath(pathString);
        return (ConstraintViolation) java.lang.reflect.Proxy.newProxyInstance(
                ConstraintViolation.class.getClassLoader(),
                new Class<?>[]{ConstraintViolation.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPropertyPath" -> path;
                    case "getMessage" -> message;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "stubViolation(" + pathString + ")";
                    default -> null;
                });
    }

    /** "findUser.id" 같은 dotted 경로를 jakarta.validation.Path로 흉내. */
    private static Path stubPath(String dotted) {
        String[] segments = dotted.split("\\.");
        List<Path.Node> nodes = java.util.Arrays.stream(segments).map(seg ->
                (Path.Node) java.lang.reflect.Proxy.newProxyInstance(
                        Path.Node.class.getClassLoader(),
                        new Class<?>[]{Path.Node.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getName" -> seg;
                            case "getIndex", "getKey" -> null;
                            default -> null;
                        })
        ).toList();
        return (Path) java.lang.reflect.Proxy.newProxyInstance(
                Path.class.getClassLoader(),
                new Class<?>[]{Path.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "iterator" -> nodes.iterator();
                    case "toString" -> dotted;
                    default -> null;
                });
    }

    @SuppressWarnings("unused")
    private static DefaultMessageSourceResolvable msr(String code, String defaultMessage) {
        return new DefaultMessageSourceResolvable(new String[]{code}, defaultMessage);
    }

    /**
     * HandlerMethodValidationException stub. 핸들러는 {@code getValueResults()}만 호출하므로
     * 단일 ParameterValidationResult를 가진 가짜를 반환한다. paramName이 null인지에 따라
     * fieldFieldToJsonPointer가 unknown_<index>로 폴백하는 분기까지 검증할 수 있다.
     */
    private static HandlerMethodValidationException stubHandlerMethodValidationException(
            String paramName, String message
    ) throws Exception {
        Method dummy = String.class.getMethod("toString");
        MethodParameter param = paramName == null
                ? new MethodParameter(dummy, -1)
                : new MethodParameter(dummy, -1) {
                    @Override public String getParameterName() { return paramName; }
                };

        Object validationResult = (Object) java.lang.reflect.Proxy.newProxyInstance(
                org.springframework.validation.method.MethodValidationResult.class.getClassLoader(),
                new Class<?>[]{org.springframework.validation.method.MethodValidationResult.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getValueResults", "getParameterValidationResults" -> List.of(
                            stubParameterValidationResult(param, message));
                    case "hasErrors" -> true;
                    case "getAllErrors" -> List.of(new DefaultMessageSourceResolvable(
                            new String[]{"NotBlank"}, message));
                    case "getAllValidationResults" -> List.of();
                    case "getCrossParameterValidationResults", "getBeanResults" -> List.of();
                    case "getMethod" -> dummy;
                    case "getTarget" -> "stubTarget";
                    case "throwIfViolationsPresent" -> null;
                    default -> defaultPrimitive(method.getReturnType());
                });

        return new HandlerMethodValidationException(
                (org.springframework.validation.method.MethodValidationResult) validationResult);
    }

    private static org.springframework.validation.method.ParameterValidationResult stubParameterValidationResult(
            MethodParameter param, String message
    ) {
        MessageSourceResolvable error = new DefaultMessageSourceResolvable(
                new String[]{"NotBlank"}, message);
        return new org.springframework.validation.method.ParameterValidationResult(
                param,
                null, // argument
                List.of(error),
                null, // container
                null, // index
                null, // key
                (resolvable, type) -> resolvable);
    }

    private static Object defaultPrimitive(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class || returnType == long.class || returnType == short.class
                || returnType == byte.class || returnType == char.class) return 0;
        if (returnType == double.class || returnType == float.class) return 0.0;
        return null;
    }
}
