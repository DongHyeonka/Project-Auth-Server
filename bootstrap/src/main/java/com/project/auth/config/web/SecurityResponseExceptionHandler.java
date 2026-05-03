package com.project.auth.config.web;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * Threading assumption: SecurityContextHolder uses the default ThreadLocal
 * strategy. If/when async controllers (@Async, Callable, DeferredResult, WebFlux
 * adapters) are introduced, the SecurityContext must be propagated to the worker
 * thread (DelegatingSecurityContextRunnable / MODE_INHERITABLETHREADLOCAL /
 * SecurityContextHolderStrategy customization) — otherwise this handler will see
 * an empty context on the worker and incorrectly classify an authenticated user's
 * AccessDenied as 401 instead of 403. Revisit isAnonymous() to also consult
 * HttpServletRequest.getUserPrincipal() if async paths are added.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityResponseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityResponseExceptionHandler.class);

    private final ApiResultFactory apiResultFactory;

    public SecurityResponseExceptionHandler(ApiResultFactory apiResultFactory) {
        this.apiResultFactory = apiResultFactory;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<Void>> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        log.warn("Authentication required. exceptionType={} method={} requestPath={} errorCode={}",
                exception.getClass().getSimpleName(),
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                AuthErrorCode.AUTHENTICATION_REQUIRED.code());

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.AUTHENTICATION_REQUIRED))
                .body(apiResultFactory.failure(
                        AuthErrorCode.AUTHENTICATION_REQUIRED.code(),
                        AuthErrorCode.AUTHENTICATION_REQUIRED.message()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        if (isAnonymous(SecurityContextHolder.getContext().getAuthentication())) {
            log.warn("AccessDenied for anonymous principal -> 401. exceptionType={} method={} requestPath={} errorCode={}",
                    exception.getClass().getSimpleName(),
                    request.getMethod(),
                    LogSanitizer.requestPath(request.getRequestURI()),
                    AuthErrorCode.AUTHENTICATION_REQUIRED.code());

            return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.AUTHENTICATION_REQUIRED))
                    .body(apiResultFactory.failure(
                            AuthErrorCode.AUTHENTICATION_REQUIRED.code(),
                            AuthErrorCode.AUTHENTICATION_REQUIRED.message()
                    ));
        }

        log.warn("Access denied for authenticated principal. exceptionType={} method={} requestPath={} errorCode={}",
                exception.getClass().getSimpleName(),
                request.getMethod(),
                LogSanitizer.requestPath(request.getRequestURI()),
                AuthErrorCode.ACCESS_DENIED.code());

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
