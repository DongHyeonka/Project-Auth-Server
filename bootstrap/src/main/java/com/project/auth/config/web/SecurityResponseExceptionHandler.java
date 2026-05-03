package com.project.auth.config.web;

import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.logging.LogSanitizer;
import com.project.auth.config.auth.security.SecurityAuditTrailWriter;
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

import java.util.Objects;

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
 * Audit 정책: 이 advice의 모든 분기(인증 필요, 익명 401, 인증된 사용자 403)는
 * SecurityAuditTrailWriter로 audit를 기록한다. 필터 단의 SecurityExceptionHandler가
 * 기록하는 audit 채널과 동일 채널을 사용하므로, "필터 단/컨트롤러 단" 어느 경로로 들어와도
 * 보안 이벤트가 누락 없이 동일 형태로 적재된다.
 *
 * 스레드/익명 판정: SecurityContextHolder의 ThreadLocal 컨텍스트를 우선 보지만,
 * 비동기 컨트롤러로 컨텍스트가 워커 스레드로 전파되지 않은 케이스를 대비해
 * HttpServletRequest.getUserPrincipal()을 보조 신호로 사용한다. principal이 명시적으로
 * 존재하면 익명으로 분류하지 않는다 — 컨텍스트가 비어 있더라도 인증된 사용자가 잘못
 * 401로 분류되지 않도록 보호하는 방어적 폴백이다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityResponseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityResponseExceptionHandler.class);

    private final ApiResultFactory apiResultFactory;
    private final SecurityAuditTrailWriter securityAuditTrailWriter;

    public SecurityResponseExceptionHandler(
            ApiResultFactory apiResultFactory,
            SecurityAuditTrailWriter securityAuditTrailWriter
    ) {
        this.apiResultFactory = Objects.requireNonNull(apiResultFactory, "apiResultFactory must not be null");
        this.securityAuditTrailWriter = Objects.requireNonNull(
                securityAuditTrailWriter, "securityAuditTrailWriter must not be null");
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
        recordAudit(request, AuthAuditEventType.AUTHENTICATION_REQUIRED, "Authentication required.");

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
        if (isAnonymous(SecurityContextHolder.getContext().getAuthentication(), request)) {
            log.warn("AccessDenied for anonymous principal -> 401. exceptionType={} method={} requestPath={} errorCode={}",
                    exception.getClass().getSimpleName(),
                    request.getMethod(),
                    LogSanitizer.requestPath(request.getRequestURI()),
                    AuthErrorCode.AUTHENTICATION_REQUIRED.code());
            recordAudit(request, AuthAuditEventType.AUTHENTICATION_REQUIRED,
                    "Authentication required (AccessDenied for anonymous).");

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
        recordAudit(request, AuthAuditEventType.ACCESS_DENIED, "Access denied.");

        return ResponseEntity.status(ApiErrorHttpStatusMapper.map(AuthErrorCode.ACCESS_DENIED))
                .body(apiResultFactory.failure(
                        AuthErrorCode.ACCESS_DENIED.code(),
                        AuthErrorCode.ACCESS_DENIED.message()
                ));
    }

    /**
     * SecurityContext가 비어 있어도 서블릿 principal이 명시적으로 존재하면 익명이 아니다.
     * ThreadLocal 컨텍스트가 비동기 워커 스레드로 전파되지 않은 케이스에서 인증된 사용자가
     * 잘못 401로 분류되는 사고를 막기 위한 방어적 폴백.
     */
    private static boolean isAnonymous(Authentication authentication, HttpServletRequest request) {
        if (request.getUserPrincipal() != null) {
            return false;
        }
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private void recordAudit(HttpServletRequest request, AuthAuditEventType type, String description) {
        try {
            securityAuditTrailWriter.record(request, type, description);
        } catch (RuntimeException auditFailure) {
            log.warn("Security audit write failed; continuing with response rendering. type={}",
                    type, auditFailure);
        }
    }
}
