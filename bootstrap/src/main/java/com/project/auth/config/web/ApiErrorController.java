package com.project.auth.config.web;

import com.project.auth.application.support.exception.ClientFacingErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.exception.PresentationErrorCode;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;
import java.util.Objects;

/**
 * Last-resort error renderer for requests that bypass {@code @RestControllerAdvice}
 * (filter-thrown exceptions, {@code response.sendError(...)}, container-level routing
 * failures, double-faults inside other handlers).
 *
 * Preserves the container-decided status code and only normalizes the response body
 * shape to {@link ApiResult}. 5xx is collapsed to {@code COMMON-999} to avoid leaking
 * internal classification; 4xx maps to {@link PresentationErrorCode}-derived codes
 * so client SDKs can distinguish "you sent something wrong" from "the server broke".
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
        Throwable error = errorAttributes.getError(new ServletWebRequest(request));
        Map<String, Object> attributes = errorAttributes.getErrorAttributes(
                new ServletWebRequest(request),
                ErrorAttributeOptions.defaults()
        );

        int rawStatus = resolveStatusCode(attributes.get("status"));
        HttpStatus httpStatus = safeStatus(rawStatus);
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
