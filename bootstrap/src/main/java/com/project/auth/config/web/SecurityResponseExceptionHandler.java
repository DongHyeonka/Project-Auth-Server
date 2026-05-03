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
 * Spring Security 예외를 처리하는 bootstrap 계층 advice.
 *
 * presentation 모듈은 ArchUnit 규칙에 의해 org.springframework.security.core / context에
 * 직접 의존할 수 없으므로(LayerDependencyArchitectureTest 참고), 이 advice는 bootstrap에 둔다.
 *
 * AccessDeniedException은 현재 인증 상태에 따라 401/403으로 분기한다. 익명 principal이
 * 보호된 리소스에 접근한 경우는 인증이 필요하다는 의미로 401을 반환하고, 인증된 principal이
 * 권한이 부족한 경우에만 403을 반환한다. Spring Security의 ExceptionTranslationFilter가
 * 필터 단계에서 던지는 예외에 대해 적용하는 분기 로직을 컨트롤러 단(@PreAuthorize 등 메서드
 * 보안)에서 던져진 동일 예외에도 일관되게 적용한 것이다.
 *
 * 스레드 가정: SecurityContextHolder는 기본 ThreadLocal 전략을 사용한다고 가정한다.
 * 비동기 컨트롤러(@Async, Callable, DeferredResult, WebFlux adapter 등)가 도입되면
 * SecurityContext가 워커 스레드로 전파되도록 처리해야 한다(DelegatingSecurityContextRunnable
 * / MODE_INHERITABLETHREADLOCAL / SecurityContextHolderStrategy 커스터마이즈 등).
 * 그렇지 않으면 워커 스레드에서 컨텍스트가 비어 있어 인증된 사용자의 AccessDenied가 잘못
 * 401로 분류된다. 비동기 경로가 추가될 때는 isAnonymous()가 HttpServletRequest.getUserPrincipal()
 * 도 함께 참조하도록 보강한다.
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
