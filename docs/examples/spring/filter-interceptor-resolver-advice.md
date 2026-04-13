# Filter / Interceptor / Resolver / Advice 예시

## 좋은 예시

### 예시 1. request/response 수준의 공통 처리만 filter에 둔다

```java
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        filterChain.doFilter(request, response);
    }
}
```

**좋은 이유:**

- HTTP request/response concern만 다룬다
- controller 이전에 처리되어도 자연스럽다
- business/service/repository에 의존하지 않는다

### 예시 2. handler 전후의 가벼운 공통 처리는 interceptor에 둔다

```java
@Component
@RequiredArgsConstructor
public class AuditActorInterceptor implements HandlerInterceptor {

    private final AuditContextHolder auditContextHolder;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        String actorId = (String) request.getAttribute(RequestAttributes.AUTHENTICATED_ACTOR_ID);
        if (actorId != null) {
            auditContextHolder.bind(actorId);
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        auditContextHolder.clear();
    }
}

public final class RequestAttributes {

    public static final String AUTHENTICATED_ACTOR_ID = "authenticatedActorId";

    private RequestAttributes() {
    }
}
```

**좋은 이유:**

- handler 실행 전후의 공통 처리라는 interceptor 책임에 맞는다
- 인증 자체를 구현하지 않고, 인증 이후 컨텍스트 연결만 수행한다
- 핵심 business logic을 수행하지 않는다

### 예시 3. resolver는 낮은 수준의 예외만 제한적으로 변환한다

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBindingExceptionResolver implements HandlerExceptionResolver {

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) throws IOException {
        if (!(ex instanceof HttpMessageNotReadableException)) {
            return null;
        }

        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {
                  "success": false,
                  "code": "MALFORMED_JSON_REQUEST",
                  "message": "Malformed request body"
                }
                """);

        return new ModelAndView();
    }
}
```

**좋은 이유:**

- resolver를 “전역 business exception 처리기”가 아니라 저수준 예외 처리 지점으로 제한한다
- null 반환으로 다른 예외는 다음 resolver/advice에 넘긴다
- resolver 사용 이유가 명확하다

**주의:**

- 실제 프로젝트에서는 이조차도 가능하면 advice/기본 처리로 흡수할 수 있는지 먼저 검토하는 편이 낫다
- 이 예시는 “resolver가 허용되는 좁은 자리”를 보여주기 위한 예시다

### 예시 4. 에러 정책은 ErrorCode로 중앙 관리한다

```java
public enum ErrorCode {
    DOMAIN_RULE_VIOLATION(HttpStatus.CONFLICT, "DOMAIN_RULE_VIOLATION", "Domain rule violation"),
    EXTERNAL_DEPENDENCY_FAILURE(HttpStatus.BAD_GATEWAY, "EXTERNAL_DEPENDENCY_FAILURE", "Temporary external dependency failure"),
    REQUEST_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_FAILED", "Request validation failed");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
```

**좋은 이유:**

- 에러 코드, 메시지, 상태값이 분산되지 않는다
- advice가 문자열 조립 대신 매핑 책임에 집중할 수 있다

### 예시 5. 전역 예외 응답은 @RestControllerAdvice에서 ApiResult로 통일한다

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainRuleViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleDomainRuleViolation(
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.DOMAIN_RULE_VIOLATION;
        Map<String, String> metadata = requestMetadata(request);

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(
                        errorCode,
                        null,
                        metadata
                ));
    }

    @ExceptionHandler(ExternalDependencyException.class)
    public ResponseEntity<ApiResult<Void>> handleExternalDependency(
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.EXTERNAL_DEPENDENCY_FAILURE;
        Map<String, String> metadata = requestMetadata(request);

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(
                        errorCode,
                        null,
                        metadata
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        FieldError::getField,
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (first, second) -> first
                ));

        ErrorCode errorCode = ErrorCode.REQUEST_VALIDATION_FAILED;
        Map<String, String> metadata = requestMetadata(request);

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(
                        errorCode,
                        errors,
                        metadata
                ));
    }

    private Map<String, String> requestMetadata(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (!(requestId instanceof String value) || value.isBlank()) {
            return Map.of();
        }
        return Map.of("requestId", value);
    }
}
```

**좋은 이유:**

- business exception, validation exception을 한곳에서 다룬다
- 응답 포맷이 ApiResult로 일관된다
- advice는 예외를 ErrorCode로 매핑하는 책임만 가진다

### 예시 6. 성공 응답 공통 래핑은 ResponseBodyAdvice에서 처리한다

```java
@RestControllerAdvice
public class ApiResultResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<?> converterType) {
        Class<?> parameterType = returnType.getParameterType();

        return !ApiResult.class.isAssignableFrom(parameterType)
                && !ResponseEntity.class.isAssignableFrom(parameterType)
                && !Resource.class.isAssignableFrom(parameterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body == null) {
            return ApiResult.success(null);
        }

        if (body instanceof ApiResult<?>) {
            return body;
        }

        return ApiResult.success(body);
    }
}
```

**좋은 이유:**

- 성공 응답 공통화 위치가 명확하다
- controller가 반복해서 ApiResult.success(...)를 만들지 않아도 된다
- 이미 래핑된 응답은 다시 감싸지 않는다

### 예시 7. controller는 정상 흐름만 표현한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionQueryController {

    private final SessionQueryUseCase sessionQueryUseCase;

    @GetMapping("/{sessionId}")
    public ApiResult<SessionResponse> getSession(@PathVariable String sessionId) {
        SessionResponse response = sessionQueryUseCase.getSession(sessionId);
        return ApiResult.success(response);
    }
}
```

**좋은 이유:**

- controller가 예외 정책까지 떠안지 않는다
- 정상 흐름과 예외 흐름이 분리된다

## 나쁜 예시

### 예시 1. filter에서 business/service를 직접 호출한다

```java
@Component
@RequiredArgsConstructor
public class BadAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final LoginPolicyService loginPolicyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        User user = userRepository.findById(userId).orElseThrow();
        loginPolicyService.validate(user);

        filterChain.doFilter(request, response);
    }
}
```

**나쁜 이유:**

- filter에 repository/business validation이 들어갔다
- HTTP concern과 business concern이 섞였다

### 예시 2. interceptor를 보안의 주 레이어로 사용한다

```java
@Component
public class BadAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        if (request.getHeader("Authorization") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }
}
```

**나쁜 이유:**

- 인증/인가의 중심을 interceptor에 두고 있다
- security/filter chain과 역할이 충돌한다

### 예시 3. resolver를 business exception 처리의 기본 수단으로 사용한다

```java
@Component
public class BadBusinessExceptionResolver implements HandlerExceptionResolver {

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) throws IOException {
        if (ex instanceof DomainRuleViolationException) {
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {
                      "success": false,
                      "code": "DOMAIN_RULE_VIOLATION",
                      "message": "Domain rule violation"
                    }
                    """);
            return new ModelAndView();
        }

        return null;
    }
}
```

**나쁜 이유:**

- business exception 처리의 중심이 resolver로 내려갔다
- advice보다 의도가 덜 드러난다
- 응답 정책이 저수준 구현으로 흩어진다

### 예시 4. advice에서 에러 코드 문자열을 직접 하드코딩한다

```java
@RestControllerAdvice
public class BadApiExceptionHandler {

    @ExceptionHandler(DomainRuleViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleDomainRuleViolation(
            DomainRuleViolationException ex
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.fail(
                        "DOMAIN_RULE_VIOLATION",
                        ex.getMessage()
                ));
    }
}
```

**나쁜 이유:**

- 에러 코드 문자열이 advice에 박혀 있다
- 메시지 정책과 예외 메시지가 섞인다
- 코드/메시지/상태값 정책이 중앙화되지 않는다

### 예시 5. controller가 예외를 직접 잡아 ApiResult를 만든다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class BadUserController {

    private final UserRegisterUseCase userRegisterUseCase;

    @PostMapping
    public ResponseEntity<ApiResult<Void>> register(@RequestBody RegisterUserRequest request) {
        try {
            userRegisterUseCase.register(request.email(), request.password());
            return ResponseEntity.ok(ApiResult.success(null));
        } catch (DuplicateEmailException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResult.fail(ErrorCode.DOMAIN_RULE_VIOLATION));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResult.fail(ErrorCode.EXTERNAL_DEPENDENCY_FAILURE));
        }
    }
}
```

**나쁜 이유:**

- controller마다 예외 처리 로직이 중복된다
- 전역 예외 처리 규약이 깨진다

### 예시 6. ResponseBodyAdvice에서 무조건 이중 래핑한다

```java
@RestControllerAdvice
public class BadApiResultResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<?> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        return ApiResult.success(body);
    }
}
```

**나쁜 이유:**

- 이미 ApiResult인 응답도 다시 감싼다
- file response, streaming response 같은 예외 케이스를 고려하지 않았다
