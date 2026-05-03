package com.project.auth.presentation.support.exception;

import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
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
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        log.warn("Request body not readable. method={} requestPath={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                PresentationErrorCode.INVALID_REQUEST_BODY.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_REQUEST_BODY))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.INVALID_REQUEST_BODY.code(),
                        PresentationErrorCode.INVALID_REQUEST_BODY.message()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        log.warn("Method not supported. method={} requestPath={} attemptedMethod={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                LogSanitizer.normalize(exception.getMethod()),
                PresentationErrorCode.METHOD_NOT_ALLOWED.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.METHOD_NOT_ALLOWED))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.METHOD_NOT_ALLOWED.code(),
                        PresentationErrorCode.METHOD_NOT_ALLOWED.message()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingParameterException(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        log.warn("Missing request parameter. method={} requestPath={} parameter={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                LogSanitizer.normalize(exception.getParameterName()),
                PresentationErrorCode.MISSING_PARAMETER.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.MISSING_PARAMETER))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.MISSING_PARAMETER.code(),
                        PresentationErrorCode.MISSING_PARAMETER.message()
                ));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResult<Void>> handleTypeMismatchException(
            TypeMismatchException exception,
            HttpServletRequest request
    ) {
        log.warn("Type mismatch for parameter. method={} requestPath={} parameter={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                LogSanitizer.normalize(exception.getPropertyName()),
                PresentationErrorCode.TYPE_MISMATCH.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.TYPE_MISMATCH))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.TYPE_MISMATCH.code(),
                        PresentationErrorCode.TYPE_MISMATCH.message()
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        log.warn("Unsupported media type. method={} requestPath={} contentType={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
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
            HttpMediaTypeNotAcceptableException exception,
            HttpServletRequest request
    ) {
        log.warn("Not acceptable media type. method={} requestPath={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                PresentationErrorCode.NOT_ACCEPTABLE.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.NOT_ACCEPTABLE))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.NOT_ACCEPTABLE.code(),
                        PresentationErrorCode.NOT_ACCEPTABLE.message()
                ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingRequestHeaderException(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        log.warn("Missing request header. method={} requestPath={} header={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                LogSanitizer.normalize(exception.getHeaderName()),
                PresentationErrorCode.MISSING_HEADER.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.MISSING_HEADER))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.MISSING_HEADER.code(),
                        PresentationErrorCode.MISSING_HEADER.message()
                ));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResult<Void>> handleServletRequestBindingException(
            ServletRequestBindingException exception,
            HttpServletRequest request
    ) {
        log.warn("Request binding failed. method={} requestPath={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                PresentationErrorCode.REQUEST_BINDING_FAILED.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.REQUEST_BINDING_FAILED))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.REQUEST_BINDING_FAILED.code(),
                        PresentationErrorCode.REQUEST_BINDING_FAILED.message()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        log.warn("No resource found. method={} requestPath={} resourcePath={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                LogSanitizer.requestPath(exception.getResourcePath()),
                PresentationErrorCode.RESOURCE_NOT_FOUND.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.RESOURCE_NOT_FOUND))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.RESOURCE_NOT_FOUND.code(),
                        PresentationErrorCode.RESOURCE_NOT_FOUND.message()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        log.warn("Upload payload too large. method={} requestPath={} maxBytes={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                exception.getMaxUploadSize(),
                PresentationErrorCode.PAYLOAD_TOO_LARGE.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.PAYLOAD_TOO_LARGE))
                .body(apiResultFactory.failure(
                        PresentationErrorCode.PAYLOAD_TOO_LARGE.code(),
                        PresentationErrorCode.PAYLOAD_TOO_LARGE.message()
                ));
    }

    /**
     * 더 구체적인 핸들러로 잡히지 않은, 프레임워크가 직접 던진 상태 캐리어 예외 처리.
     * 상위에서 결정된 4xx vs 5xx 분기가 클라이언트까지 보존되도록 carriedStatus를 그대로 사용한다.
     *
     * 본문 분류: 5xx는 내부 분류 노출을 막기 위해 COMMON-999로 정규화하고, 4xx는
     * UNHANDLED_CLIENT_ERROR로 폴백한다. 클라이언트 SDK가 "요청 잘못" vs "서버 결함"을
     * 여전히 구분할 수 있게 하기 위함이다.
     *
     * ResponseStatusException과 ErrorResponseException은 둘 다 {@link ErrorResponse}를 구현하면서
     * 동시에 {@link Throwable}을 상속하므로 로그 호출부의 (Throwable) 캐스트가 안전하다.
     * 향후 Throwable이 아닌 ErrorResponse 구현체를 이 @ExceptionHandler 목록에 추가하면
     * 런타임 ClassCastException이 발생하므로, 이 핸들러는 명시한 두 예외 타입으로만 한정한다.
     */
    @ExceptionHandler({ResponseStatusException.class, ErrorResponseException.class})
    public ResponseEntity<ApiResult<Void>> handleErrorResponseException(
            ErrorResponse exception,
            HttpServletRequest request
    ) {
        HttpStatusCode carriedStatus = exception.getStatusCode();
        int statusValue = carriedStatus.value();

        if (statusValue >= 500) {
            log.error("Framework-thrown 5xx status. method={} requestPath={} status={}",
                    request.getMethod(),
                    LogSanitizer.requestPath(request.getRequestURI()),
                    statusValue,
                    (Throwable) exception);
            return ResponseEntity.status(carriedStatus)
                    .body(apiResultFactory.failure(
                            CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                            CommonErrorCode.INTERNAL_SERVER_ERROR.message()
                    ));
        }

        log.warn("Framework-thrown 4xx status. method={} requestPath={} status={} errorCode={}",
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                statusValue,
                PresentationErrorCode.UNHANDLED_CLIENT_ERROR.code());
        return ResponseEntity.status(carriedStatus)
                .body(apiResultFactory.failure(
                        PresentationErrorCode.UNHANDLED_CLIENT_ERROR.code(),
                        PresentationErrorCode.UNHANDLED_CLIENT_ERROR.message()
                ));
    }

}
