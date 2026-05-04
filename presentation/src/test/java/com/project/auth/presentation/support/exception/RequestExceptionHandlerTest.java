package com.project.auth.presentation.support.exception;

import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import com.project.auth.presentation.support.response.FixedApiResultFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestExceptionHandler를 컨테이너 없이 직접 호출하는 단위 테스트.
 *
 * 통합 테스트는 핸들러 동작을 검증하지만, 모듈 경계 때문에 JaCoCo가 presentation
 * 모듈의 라인 흔적을 카운트하지 못한다. 본 단위 테스트는 같은 동작을 presentation
 * 모듈의 jacoco execution data에 기록되도록 만들어, 모듈 단위 측정에서도 핸들러가
 * 정직하게 보이도록 한다.
 */
class RequestExceptionHandlerTest {

    private final ApiResultFactory apiResultFactory = new FixedApiResultFactory();
    private final RequestExceptionHandler handler = new RequestExceptionHandler(apiResultFactory);
    private final HttpServletRequest request = stubRequest();

    @Test
    void handleMessageNotReadableException_returns_400_invalid_request_body() {
        ResponseEntity<ApiResult<Void>> response = handler.handleMessageNotReadableException(
                new HttpMessageNotReadableException("malformed", new EmptyInputMessage()), request);

        assertResponse(response, HttpStatus.BAD_REQUEST, PresentationErrorCode.INVALID_REQUEST_BODY);
    }

    @Test
    void handleMethodNotSupportedException_returns_405() {
        ResponseEntity<ApiResult<Void>> response = handler.handleMethodNotSupportedException(
                new HttpRequestMethodNotSupportedException("PATCH"), request);

        assertResponse(response, HttpStatus.METHOD_NOT_ALLOWED, PresentationErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void handleMissingParameterException_returns_400_missing_parameter() {
        ResponseEntity<ApiResult<Void>> response = handler.handleMissingParameterException(
                new MissingServletRequestParameterException("name", "String"), request);

        assertResponse(response, HttpStatus.BAD_REQUEST, PresentationErrorCode.MISSING_PARAMETER);
    }

    @Test
    void handleTypeMismatchException_returns_400_type_mismatch() {
        TypeMismatchException ex = new TypeMismatchException("abc", Long.class);
        ResponseEntity<ApiResult<Void>> response = handler.handleTypeMismatchException(ex, request);

        assertResponse(response, HttpStatus.BAD_REQUEST, PresentationErrorCode.TYPE_MISMATCH);
    }

    @Test
    void handleMediaTypeNotSupportedException_returns_415() {
        ResponseEntity<ApiResult<Void>> response = handler.handleMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON)),
                request);

        assertResponse(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void handleMediaTypeNotAcceptableException_returns_406() {
        ResponseEntity<ApiResult<Void>> response = handler.handleMediaTypeNotAcceptableException(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);

        assertResponse(response, HttpStatus.NOT_ACCEPTABLE, PresentationErrorCode.NOT_ACCEPTABLE);
    }

    @Test
    void handleMissingRequestHeaderException_returns_400_missing_header() throws Exception {
        Method dummy = String.class.getMethod("toString");
        org.springframework.core.MethodParameter param = new org.springframework.core.MethodParameter(dummy, -1);
        ResponseEntity<ApiResult<Void>> response = handler.handleMissingRequestHeaderException(
                new MissingRequestHeaderException("X-Trace-Id", param), request);

        assertResponse(response, HttpStatus.BAD_REQUEST, PresentationErrorCode.MISSING_HEADER);
    }

    @Test
    void handleServletRequestBindingException_returns_400_request_binding_failed() {
        ResponseEntity<ApiResult<Void>> response = handler.handleServletRequestBindingException(
                new ServletRequestBindingException("bind failed"), request);

        assertResponse(response, HttpStatus.BAD_REQUEST, PresentationErrorCode.REQUEST_BINDING_FAILED);
    }

    @Test
    void handleNoResourceFoundException_returns_404_resource_not_found() {
        ResponseEntity<ApiResult<Void>> response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/missing", "/missing"), request);

        assertResponse(response, HttpStatus.NOT_FOUND, PresentationErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void handleMaxUploadSizeExceededException_returns_413_payload_too_large() {
        ResponseEntity<ApiResult<Void>> response = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(1024L), request);

        assertResponse(response, HttpStatus.CONTENT_TOO_LARGE, PresentationErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void handleErrorResponseException_preserves_4xx_status_with_unhandled_client_error() {
        ResponseEntity<ApiResult<Void>> response = handler.handleErrorResponseException(
                new ResponseStatusException(HttpStatus.CONFLICT), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(PresentationErrorCode.UNHANDLED_CLIENT_ERROR.code());
    }

    @Test
    void handleErrorResponseException_collapses_5xx_to_common_999() {
        ResponseEntity<ApiResult<Void>> response = handler.handleErrorResponseException(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("COMMON-999");
    }

    @Test
    void handleErrorResponseException_handles_error_response_exception_subtype() {
        ResponseEntity<ApiResult<Void>> response = handler.handleErrorResponseException(
                new ErrorResponseException(HttpStatusCode.valueOf(418)), request);

        assertThat(response.getStatusCode().value()).isEqualTo(418);
        assertThat(response.getBody().code()).isEqualTo(PresentationErrorCode.UNHANDLED_CLIENT_ERROR.code());
    }

    private static void assertResponse(
            ResponseEntity<ApiResult<Void>> response,
            HttpStatus expectedStatus,
            PresentationErrorCode expectedCode
    ) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo(expectedCode.code());
        assertThat(response.getBody().message()).isEqualTo(expectedCode.message());
    }

    /**
     * 핸들러는 method/requestURI만 사용한다. JDK 동적 프록시로 두 메서드만 답하고
     * 나머지는 기본 null/0/false를 돌려주도록 한다(필요 시 NPE 방지를 위해 null 반환).
     */
    private static HttpServletRequest stubRequest() {
        return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getRequestURI" -> "/test/path";
                    default -> defaultReturn(method.getReturnType());
                });
    }

    private static Object defaultReturn(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class || returnType == long.class || returnType == short.class
                || returnType == byte.class || returnType == char.class) return 0;
        if (returnType == double.class || returnType == float.class) return 0.0;
        if (returnType == void.class) return null;
        return null;
    }

    private static final class EmptyInputMessage implements HttpInputMessage {
        @Override public InputStream getBody() { return new ByteArrayInputStream(new byte[0]); }
        @Override public org.springframework.http.HttpHeaders getHeaders() { return new org.springframework.http.HttpHeaders(); }
    }
}
