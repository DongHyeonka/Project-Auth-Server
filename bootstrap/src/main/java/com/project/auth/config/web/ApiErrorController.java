package com.project.auth.config.web;

import com.project.auth.application.support.exception.ClientFacingErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.presentation.support.exception.PresentationErrorCode;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Objects;

/**
 * {@code @RestControllerAdvice}를 우회하는 요청(필터에서 던져진 예외,
 * {@code response.sendError(...)} 호출, 컨테이너 레벨 라우팅 실패, 다른 핸들러 내부의
 * 이중 폴트 등)에 대한 최후의 에러 렌더러.
 *
 * 컨테이너가 결정한 상태 코드는 그대로 보존하고, 본문만 {@link ApiResult} 형태로 정규화한다.
 * 5xx는 내부 분류 노출을 막기 위해 {@code COMMON-999}로 정규화하고, 4xx는
 * {@link PresentationErrorCode} 기반 코드로 매핑하여 클라이언트 SDK가
 * "요청이 잘못됐다"와 "서버가 깨졌다"를 구분할 수 있게 한다.
 */
@RestController
class ApiErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

    private final ErrorAttributes errorAttributes;
    private final ApiResultFactory apiResultFactory;

    ApiErrorController(ErrorAttributes errorAttributes, ApiResultFactory apiResultFactory) {
        this.errorAttributes = Objects.requireNonNull(errorAttributes, "errorAttributes must not be null");
        this.apiResultFactory = Objects.requireNonNull(apiResultFactory, "apiResultFactory must not be null");
    }

    @RequestMapping("/error")
    ResponseEntity<ApiResult<Void>> error(HttpServletRequest request) {
        // 서블릿 attribute에서 status를 직접 읽는다.
        // ErrorAttributes.getErrorAttributes(...)를 호출하지 않는 이유는, 향후
        // ErrorAttributeOptions 설정 변경(예: 스택트레이스 포함 옵션)이 이 컨트롤러가
        // 노출하는 정보를 본의 아니게 확장하지 못하게 하기 위함이다.
        int rawStatus = resolveStatusCode(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        HttpStatus httpStatus = safeStatus(rawStatus);
        Throwable error = errorAttributes.getError(new ServletWebRequest(request));
        String requestPath = LogSanitizer.requestPath(originalRequestPath(request));
        String method = request.getMethod();

        if (httpStatus.is5xxServerError() && error != null) {
            log.error("Unhandled exception. method={} requestPath={} status={}",
                    method, requestPath, httpStatus.value(), error);
        } else if (httpStatus.is5xxServerError()) {
            log.error("Unhandled error response. method={} requestPath={} status={}",
                    method, requestPath, httpStatus.value());
        } else {
            log.warn("Unhandled error response. method={} requestPath={} status={}",
                    method, requestPath, httpStatus.value());
        }

        ClientFacingErrorCode errorCode = classify(httpStatus);

        return ResponseEntity.status(httpStatus)
                .body(apiResultFactory.failure(errorCode.code(), errorCode.message()));
    }

    private static ClientFacingErrorCode classify(HttpStatus status) {
        if (status.is5xxServerError()) {
            return CommonErrorCode.INTERNAL_SERVER_ERROR;
        }
        return switch (status) {
            case NOT_FOUND -> PresentationErrorCode.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> PresentationErrorCode.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case NOT_ACCEPTABLE -> PresentationErrorCode.NOT_ACCEPTABLE;
            case CONTENT_TOO_LARGE -> PresentationErrorCode.PAYLOAD_TOO_LARGE;
            default -> PresentationErrorCode.UNHANDLED_CLIENT_ERROR;
        };
    }

    private static HttpStatus safeStatus(int rawStatus) {
        try {
            return HttpStatus.valueOf(rawStatus);
        } catch (IllegalArgumentException ignored) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private static int resolveStatusCode(Object status) {
        if (status instanceof Integer value) {
            return value;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private static String originalRequestPath(HttpServletRequest request) {
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path instanceof String requestUri && !requestUri.isBlank()) {
            return requestUri;
        }
        return request.getRequestURI();
    }
}
