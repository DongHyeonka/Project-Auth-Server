package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestExceptionHandler.class);

    private final ApiResultFactory apiResultFactory;

    public RequestExceptionHandler(ApiResultFactory apiResultFactory) {
        this.apiResultFactory = apiResultFactory;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        log.warn("Request body not readable. errorCode={}", PresentationErrorCode.INVALID_REQUEST_BODY.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_REQUEST_BODY))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.INVALID_REQUEST_BODY.code(),
                        PresentationErrorCode.INVALID_REQUEST_BODY.message()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        log.warn("Method not supported. method={} errorCode={}",
                LogSanitizer.normalize(exception.getMethod()), PresentationErrorCode.METHOD_NOT_ALLOWED.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.METHOD_NOT_ALLOWED))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.METHOD_NOT_ALLOWED.code(),
                        PresentationErrorCode.METHOD_NOT_ALLOWED.message()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingParameterException(
            MissingServletRequestParameterException exception
    ) {
        log.warn("Missing request parameter. parameter={} errorCode={}",
                LogSanitizer.normalize(exception.getParameterName()), PresentationErrorCode.MISSING_PARAMETER.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.MISSING_PARAMETER))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.MISSING_PARAMETER.code(),
                        PresentationErrorCode.MISSING_PARAMETER.message()
                ));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResult<Void>> handleTypeMismatchException(
            TypeMismatchException exception
    ) {
        log.warn("Type mismatch for parameter. parameter={} errorCode={}",
                LogSanitizer.normalize(exception.getPropertyName()), PresentationErrorCode.INVALID_INPUT.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_INPUT))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.INVALID_INPUT.code(),
                        PresentationErrorCode.INVALID_INPUT.message()
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception
    ) {
        log.warn("Unsupported media type. contentType={} errorCode={}",
                LogSanitizer.normalize(String.valueOf(exception.getContentType())),
                PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE.code(),
                        PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE.message()
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResult<Void>> handleMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException exception
    ) {
        log.warn("Not acceptable media type. errorCode={}", PresentationErrorCode.NOT_ACCEPTABLE.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.NOT_ACCEPTABLE))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.NOT_ACCEPTABLE.code(),
                        PresentationErrorCode.NOT_ACCEPTABLE.message()
                ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingRequestHeaderException(
            MissingRequestHeaderException exception
    ) {
        log.warn("Missing request header. header={} errorCode={}",
                LogSanitizer.normalize(exception.getHeaderName()), PresentationErrorCode.MISSING_HEADER.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.MISSING_HEADER))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.MISSING_HEADER.code(),
                        PresentationErrorCode.MISSING_HEADER.message()
                ));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResult<Void>> handleServletRequestBindingException(
            ServletRequestBindingException exception
    ) {
        log.warn("Request binding failed. errorCode={}", PresentationErrorCode.REQUEST_BINDING_FAILED.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.REQUEST_BINDING_FAILED))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.REQUEST_BINDING_FAILED.code(),
                        PresentationErrorCode.REQUEST_BINDING_FAILED.message()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        log.warn("No resource found. resourcePath={} errorCode={}",
                LogSanitizer.normalize(exception.getResourcePath()), PresentationErrorCode.RESOURCE_NOT_FOUND.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.RESOURCE_NOT_FOUND))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.RESOURCE_NOT_FOUND.code(),
                        PresentationErrorCode.RESOURCE_NOT_FOUND.message()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception
    ) {
        log.warn("Upload payload too large. maxBytes={} errorCode={}",
                exception.getMaxUploadSize(), PresentationErrorCode.PAYLOAD_TOO_LARGE.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.PAYLOAD_TOO_LARGE))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.PAYLOAD_TOO_LARGE.code(),
                        PresentationErrorCode.PAYLOAD_TOO_LARGE.message()
                ));
    }

    /**
     * Handles framework-thrown status carriers that aren't already covered by a
     * more specific handler. Preserves the carried status code so that 4xx vs 5xx
     * routing decisions made upstream survive to the client.
     *
     * Body classification: 5xx collapses to COMMON-999 to avoid leaking internal
     * categorization, 4xx falls back to UNHANDLED_CLIENT_ERROR so client SDKs can
     * still distinguish "your request was wrong" from a server-side fault.
     */
    @ExceptionHandler({ResponseStatusException.class, ErrorResponseException.class})
    public ResponseEntity<ApiResult<Void>> handleErrorResponseException(
            org.springframework.web.ErrorResponse exception
    ) {
        HttpStatus carriedStatus = HttpStatus.resolve(exception.getStatusCode().value());
        if (carriedStatus == null) {
            carriedStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (carriedStatus.is5xxServerError()) {
            log.error("Framework-thrown 5xx status. status={}", carriedStatus.value(), (Throwable) exception);
            return ResponseEntity.status(carriedStatus)
                    .body(apiResultFactory.failure(
                            com.project.auth.application.support.exception.CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                            com.project.auth.application.support.exception.CommonErrorCode.INTERNAL_SERVER_ERROR.message()
                    ));
        }

        log.warn("Framework-thrown 4xx status. status={} errorCode={}",
                carriedStatus.value(), PresentationErrorCode.UNHANDLED_CLIENT_ERROR.code());
        return ResponseEntity.status(carriedStatus)
                .body(apiResultFactory.failure(
                        PresentationErrorCode.UNHANDLED_CLIENT_ERROR.code(),
                        PresentationErrorCode.UNHANDLED_CLIENT_ERROR.message()
                ));
    }

}
