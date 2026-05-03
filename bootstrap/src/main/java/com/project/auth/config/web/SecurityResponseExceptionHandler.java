package com.project.auth.config.web;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bootstrap-layer advice for Spring Security exceptions. Lives here (not in
 * presentation) because the presentation module is architecturally forbidden to
 * depend on org.springframework.security.core / context — see ArchUnit rules in
 * LayerDependencyArchitectureTest.
 *
 * AccessDeniedException is split between 401 and 403 based on the current
 * authentication: an anonymous principal hitting a protected resource needs to
 * authenticate (401), whereas an authenticated principal lacking the required
 * authority is genuinely forbidden (403). This mirrors Spring Security's
 * ExceptionTranslationFilter behavior for filter-thrown exceptions, but applies
 * the same rule when the exception escapes from a controller (e.g., method
 * security via @PreAuthorize).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityResponseExceptionHandler {

    private final ApiResultFactory apiResultFactory;

    public SecurityResponseExceptionHandler(ApiResultFactory apiResultFactory) {
        this.apiResultFactory = apiResultFactory;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<Void>> handleAuthenticationException(AuthenticationException exception) {
        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.AUTHENTICATION_REQUIRED))
                .body(apiResultFactory.failure(
                        AuthErrorCode.AUTHENTICATION_REQUIRED.code(),
                        AuthErrorCode.AUTHENTICATION_REQUIRED.message()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        if (isAnonymous(SecurityContextHolder.getContext().getAuthentication())) {
            return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.AUTHENTICATION_REQUIRED))
                    .body(apiResultFactory.failure(
                            AuthErrorCode.AUTHENTICATION_REQUIRED.code(),
                            AuthErrorCode.AUTHENTICATION_REQUIRED.message()
                    ));
        }
        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.ACCESS_DENIED))
                .body(apiResultFactory.failure(
                        AuthErrorCode.ACCESS_DENIED.code(),
                        AuthErrorCode.ACCESS_DENIED.message()
                ));
    }

    private static boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }
}
