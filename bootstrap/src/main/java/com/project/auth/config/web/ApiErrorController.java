package com.project.auth.config.web;

import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;
import java.util.Objects;

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

        int statusCode = resolveStatusCode(attributes.get("status"));
        String requestPath = LogSanitizer.requestPath(originalRequestPath(request));
        String method = request.getMethod();

        if (statusCode >= 500 && error != null) {
            log.error("Unhandled exception. method={} requestPath={}", method, requestPath, error);
        } else {
            log.warn("Unhandled error response. method={} requestPath={} status={}", method, requestPath, statusCode);
        }

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .body(apiResultFactory.failure(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.message()
                ));
    }

    private static int resolveStatusCode(Object status) {
        if (status instanceof Integer value) {
            return value;
        }
        return 500;
    }

    private static String originalRequestPath(HttpServletRequest request) {
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path instanceof String requestUri && !requestUri.isBlank()) {
            return requestUri;
        }
        return request.getRequestURI();
    }
}
