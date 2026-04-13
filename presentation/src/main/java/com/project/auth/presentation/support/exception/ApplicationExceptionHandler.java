package com.project.auth.presentation.support.exception;

import com.project.auth.application.auth.exception.AuthErrorCode;
import com.project.auth.application.support.exception.BusinessException;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.presentation.support.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApplicationExceptionHandler {

    private static final String ANONYMOUS_PRINCIPAL = "anonymous";
    private static final String HASH_PREFIX = "sha256:";
    private static final int HASH_HEX_LENGTH = 16;
    private static final int MAX_LOG_VALUE_LENGTH = 200;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private static final Logger log = LoggerFactory.getLogger(ApplicationExceptionHandler.class);

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAuthorizationDeniedException(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Access denied. actorId={} method={} requestPath={}",
                resolveActorId(request),
                request.getMethod(),
                normalizeLogValue(request.getRequestURI())
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.ACCESS_DENIED))
                .body(ApiResult.failure(
                        AuthErrorCode.ACCESS_DENIED.code(),
                        AuthErrorCode.ACCESS_DENIED.message()
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Business exception. errorCode={} method={} requestPath={}",
                exception.getErrorCode().code(),
                request.getMethod(),
                normalizeLogValue(request.getRequestURI())
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(exception.getErrorCode()))
                .body(ApiResult.failure(exception.getErrorCode().code(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ApiResult<Void>> handleMessageNotWritableException(
            HttpMessageNotWritableException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Response body not writable. method={} requestPath={}",
                request.getMethod(),
                normalizeLogValue(request.getRequestURI()),
                exception
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(PresentationErrorCode.MESSAGE_NOT_WRITABLE))
                .body(ApiResult.failure(
                        PresentationErrorCode.MESSAGE_NOT_WRITABLE.code(),
                        PresentationErrorCode.MESSAGE_NOT_WRITABLE.message()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled exception. method={} requestPath={}",
                request.getMethod(),
                normalizeLogValue(request.getRequestURI()),
                exception
        );

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(CommonErrorCode.INTERNAL_SERVER_ERROR))
                .body(ApiResult.failure(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.message()
                ));
    }

    private String resolveActorId(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return actorId(principal.getName());
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return actorId(authentication.getName());
        }

        return ANONYMOUS_PRINCIPAL;
    }

    private static String actorId(String value) {
        if (value == null || value.isBlank() || ANONYMOUS_PRINCIPAL.equals(value)) {
            return ANONYMOUS_PRINCIPAL;
        }
        String normalized = normalizeLogValue(value);
        if (normalized.contains("@")) {
            return maskEmail(normalized);
        }
        return HASH_PREFIX + sha256Hex(normalized).substring(0, HASH_HEX_LENGTH);
    }

    private static String normalizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        StringBuilder builder = new StringBuilder(Math.min(value.length(), MAX_LOG_VALUE_LENGTH));
        for (int index = 0; index < value.length() && builder.length() < MAX_LOG_VALUE_LENGTH; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character) || character == '='
                    || character == '|') {
                builder.append('_');
            } else {
                builder.append(character);
            }
        }
        if (value.length() > MAX_LOG_VALUE_LENGTH) {
            builder.append("...");
        }
        return builder.toString();
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        String prefix = localPart.length() == 1 ? localPart : localPart.substring(0, Math.min(localPart.length(), 2));
        return prefix + "***@" + domain;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX_FORMAT.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }
    }
}
