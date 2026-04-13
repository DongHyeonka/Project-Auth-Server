# Trace / Principal / Path Recording 예시

## 좋은 예시

### 예시 1. controller는 현재 사용자 식별자를 명시적으로 받는다

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "userId")
public @interface CurrentUserId {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserQueryController {

    private final UserQueryUseCase userQueryUseCase;

    @GetMapping("/{userId}")
    public ApiResult<UserResponse> getUser(
            @CurrentUserId String actorId,
            @PathVariable String userId
    ) {
        UserResult result = userQueryUseCase.getUser(actorId, userId);
        return ApiResult.success(new UserResponse(result.userId(), result.email()));
    }
}
```

**좋은 이유:**

- principal 접근이 controller 시그니처에서 드러난다
- SecurityContextHolder 직접 접근이 없다
- application에는 최소 actor 정보만 전달한다

### 예시 2. 공통 요청 로그는 한 곳에서 남긴다

```java
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startNanos = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            log.info("Completed request. requestPath={} method={} status={} durationMs={}",
                    request.getRequestURI(),
                    request.getMethod(),
                    response.getStatus(),
                    durationMs);
        }
    }
}
```

**좋은 이유:**

- 대표 요청 로그가 공통 위치에 있다
- requestPath와 method/status/duration이 일관되게 남는다
- controller마다 요청 로그를 복붙하지 않는다

### 예시 3. principal은 내부 식별자만 남긴다

```java
log.warn("Rejected request. requestPath={} actorId={} errorCode={}",
        requestPath, actorId, "ACCESS_DENIED");
```

**좋은 이유:**

- 이메일/토큰 같은 민감정보 대신 내부 식별자를 남긴다
- principal 검색 가능성과 개인정보 보호를 함께 고려한다

### 예시 4. route template를 별도 필드로 둘 수 있다

```java
log.info("Completed request. requestPath={} route={} method={} status={} durationMs={}",
        "/api/v1/users/123",
        "/api/v1/users/{userId}",
        "GET",
        200,
        21);
```

**좋은 이유:**

- 실제 요청과 집계용 route를 분리할 수 있다
- 고카디널리티 문제를 운영에서 다루기 쉬워진다

## 나쁜 예시

### 예시 1. controller가 SecurityContextHolder를 직접 읽는다

```java
@GetMapping("/api/v1/me")
public ApiResult<String> me() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();

    log.info("Current request. path={} principal={}", request.getRequestURI(), principal);
    return ApiResult.success(principal.getUserId());
}
```

**나쁜 이유:**

- principal 접근과 로깅 규칙이 controller에 퍼진다
- principal 전체 객체가 로그에 노출될 수 있다
- current user 접근 방식이 일관되지 않다

### 예시 2. query string 전체를 기본 로그에 남긴다

```java
log.info("Incoming request. requestPath={} query={}",
        request.getRequestURI(),
        request.getQueryString());
```

**나쁜 이유:**

- 검색어, 토큰, 식별자 등 민감정보가 섞일 수 있다
- 운영 로그에 노이즈가 많아진다

### 예시 3. 같은 의미를 여러 키 이름으로 섞는다

```java
log.info("Completed request. uri={} userId={}", requestPath, actorId);
log.info("Failed request. path={} principalId={}", requestPath, actorId);
```

**나쁜 이유:**

- uri/path/requestPath, userId/actorId/principalId가 혼용된다
- 검색/집계 규칙이 깨진다

### 예시 4. 요청 로그를 여러 레이어에서 반복한다

```java
log.info("Controller request. requestPath={}", requestPath);
log.info("Service request. requestPath={}", requestPath);
log.info("Client request. requestPath={}", requestPath);
```

**나쁜 이유:**

- 대표 요청 로그가 중복된다
- 실제 중요한 비즈니스/연동 로그가 묻힌다
