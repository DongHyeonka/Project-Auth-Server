package com.project.auth.presentation.support.exception;

import com.project.auth.presentation.support.response.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        log.warn("Request body not readable. errorCode={}", PresentationErrorCode.INVALID_REQUEST_BODY.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_REQUEST_BODY))
                .body(ApiResult.failure(
                        PresentationErrorCode.INVALID_REQUEST_BODY.code(),
                        PresentationErrorCode.INVALID_REQUEST_BODY.message()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        log.warn("Method not supported. method={} errorCode={}",
                normalizeLogValue(exception.getMethod()), PresentationErrorCode.METHOD_NOT_ALLOWED.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.METHOD_NOT_ALLOWED))
                .body(ApiResult.failure(
                        PresentationErrorCode.METHOD_NOT_ALLOWED.code(),
                        PresentationErrorCode.METHOD_NOT_ALLOWED.message()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingParameterException(
            MissingServletRequestParameterException exception
    ) {
        log.warn("Missing request parameter. parameter={} errorCode={}",
                normalizeLogValue(exception.getParameterName()), PresentationErrorCode.MISSING_PARAMETER.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.MISSING_PARAMETER))
                .body(ApiResult.failure(
                        PresentationErrorCode.MISSING_PARAMETER.code(),
                        PresentationErrorCode.MISSING_PARAMETER.message()
                ));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResult<Void>> handleTypeMismatchException(
            TypeMismatchException exception
    ) {
        log.warn("Type mismatch for parameter. parameter={} errorCode={}",
                normalizeLogValue(exception.getPropertyName()), PresentationErrorCode.INVALID_INPUT.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.INVALID_INPUT))
                .body(ApiResult.failure(
                        PresentationErrorCode.INVALID_INPUT.code(),
                        PresentationErrorCode.INVALID_INPUT.message()
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception
    ) {
        log.warn("Unsupported media type. contentType={} errorCode={}",
                normalizeLogValue(String.valueOf(exception.getContentType())),
                PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE))
                .body(ApiResult.failure(
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
                .body(ApiResult.failure(
                        PresentationErrorCode.NOT_ACCEPTABLE.code(),
                        PresentationErrorCode.NOT_ACCEPTABLE.message()
                ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingRequestHeaderException(
            MissingRequestHeaderException exception
    ) {
        log.warn("Missing request header. header={} errorCode={}",
                normalizeLogValue(exception.getHeaderName()), PresentationErrorCode.MISSING_HEADER.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.MISSING_HEADER))
                .body(ApiResult.failure(
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
                .body(ApiResult.failure(
                        PresentationErrorCode.REQUEST_BINDING_FAILED.code(),
                        PresentationErrorCode.REQUEST_BINDING_FAILED.message()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        log.warn("No resource found. resourcePath={} errorCode={}",
                normalizeLogValue(exception.getResourcePath()), PresentationErrorCode.RESOURCE_NOT_FOUND.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.RESOURCE_NOT_FOUND))
                .body(ApiResult.failure(
                        PresentationErrorCode.RESOURCE_NOT_FOUND.code(),
                        PresentationErrorCode.RESOURCE_NOT_FOUND.message()
                ));
    }

    private static String normalizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        int maxLength = 200;
        StringBuilder builder = new StringBuilder(Math.min(value.length(), maxLength));
        for (int index = 0; index < value.length() && builder.length() < maxLength; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character) || character == '='
                    || character == '|') {
                builder.append('_');
            } else {
                builder.append(character);
            }
        }
        if (value.length() > maxLength) {
            builder.append("...");
        }
        return builder.toString();
    }
}
