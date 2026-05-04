package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.application.support.exception.ClientFacingErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import com.project.auth.presentation.support.response.FixedApiResultFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApplicationExceptionHandler를 컨테이너 없이 직접 호출하는 단위 테스트.
 * 모듈 단위 jacoco 측정에서 카운트되도록 함이 목적.
 */
class ApplicationExceptionHandlerTest {

    private final ApiResultFactory apiResultFactory = new FixedApiResultFactory();
    private final ApplicationExceptionHandler handler = new ApplicationExceptionHandler(apiResultFactory);
    private final HttpServletRequest request = stubRequest();

    @Test
    void handleBusinessException_maps_business_error_code_to_status_and_body() {
        ResponseEntity<ApiResult<Void>> response = handler.handleBusinessException(
                new TestBusinessException(AuthErrorCode.KEYCLOAK_USER_NOT_FOUND), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo(AuthErrorCode.KEYCLOAK_USER_NOT_FOUND.code());
    }

    @Test
    void handleMessageNotWritableException_returns_500_when_response_not_committed() {
        HttpServletResponse response = stubResponse(false);
        ResponseEntity<ApiResult<Void>> result = handler.handleMessageNotWritableException(
                new HttpMessageNotWritableException("nope"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo(PresentationErrorCode.MESSAGE_NOT_WRITABLE.code());
    }

    @Test
    void handleMessageNotWritableException_returns_empty_500_when_response_committed() {
        HttpServletResponse response = stubResponse(true);
        ResponseEntity<ApiResult<Void>> result = handler.handleMessageNotWritableException(
                new HttpMessageNotWritableException("nope"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isNull();
    }

    @Test
    void handleUncaughtException_always_returns_500_common_999() {
        ResponseEntity<ApiResult<Void>> response = handler.handleUncaughtException(
                new RuntimeException("simulated"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.code());
    }

    private static HttpServletRequest stubRequest() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getRequestURI" -> "/test/path";
                    default -> defaultReturn(method.getReturnType());
                });
    }

    private static HttpServletResponse stubResponse(boolean committed) {
        AtomicBoolean flag = new AtomicBoolean(committed);
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isCommitted" -> flag.get();
                    default -> defaultReturn(method.getReturnType());
                });
    }

    private static Object defaultReturn(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class || returnType == long.class || returnType == short.class
                || returnType == byte.class || returnType == char.class) return 0;
        if (returnType == double.class || returnType == float.class) return 0.0;
        return null;
    }

    private static final class TestBusinessException extends BusinessException {
        private TestBusinessException(ClientFacingErrorCode errorCode) {
            super(errorCode);
        }
    }
}
