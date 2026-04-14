# Authentication Object Access 예시

## 좋은 예시

### 예시 1. 프로젝트 전용 @CurrentUser를 정의한다

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {
}
```

**좋은 이유:**

- controller가 Spring Security 애노테이션에 직접 결합되지 않는다
- 현재 사용자 접근 규칙이 한 파일에 모인다
- Spring 공식 문서도 같은 메타 애노테이션 방식을 예시로 보여 준다.

### 예시 2. controller는 전용 현재 사용자 타입만 받는다

```java
public record AuthenticatedUser(
        String userId,
        Set<String> authorities
) {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
public class SessionCommandController {

    private final CreateSessionUseCase createSessionUseCase;

    @PostMapping
    public ApiResult<CreateSessionResponse> create(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody CreateSessionRequest request
    ) {
        CreateSessionResult result = createSessionUseCase.create(
                new CreateSessionCommand(
                        currentUser.userId(),
                        request.email(),
                        request.password()
                )
        );

        return ApiResult.success(new CreateSessionResponse(
                result.sessionId(),
                result.accessToken()
        ));
    }
}
```

**좋은 이유:**

- controller가 현재 사용자 접근을 명시적으로 드러낸다
- application에는 필요한 값만 전달한다
- SecurityContextHolder 직접 접근이 없다

### 예시 3. ID만 필요하면 claim/field만 바로 주입한다

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "userId")
public @interface CurrentUserId {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/password")
public class PasswordController {

    private final ChangePasswordUseCase changePasswordUseCase;

    @PostMapping("/change")
    public ApiResult<Void> changePassword(
            @CurrentUserId String userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        changePasswordUseCase.change(
                new ChangePasswordCommand(
                        userId,
                        request.currentPassword(),
                        request.newPassword()
                )
        );

        return ApiResult.success(null);
    }
}
```

**좋은 이유:**

- 필요한 최소 actor 정보만 유스케이스로 간다
- current user 타입 전체를 넘기지 않아도 된다
- 공식 문서의 expression 기반 메타 애노테이션 패턴과 맞는다.

### 예시 4. Principal은 단순 확인 endpoint에 제한적으로 쓴다

```java
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    @GetMapping
    public ApiResult<Map<String, String>> me(Principal principal) {
        return ApiResult.success(Map.of("name", principal.getName()));
    }
}
```

**좋은 이유:**

- 단순 identity 확인 수준에는 충분하다
- 복잡한 Security 타입을 노출하지 않는다
- Spring MVC가 공식 지원하는 기본 method argument다.

## 나쁜 예시

### 예시 1. controller가 SecurityContextHolder를 직접 읽는다

```java
@RestController
@RequestMapping("/api/v1/me")
public class BadMeController {

    @GetMapping
    public ApiResult<String> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        return ApiResult.success(principal.getUserId());
    }
}
```

**나쁜 이유:**

- controller가 보안 저장소 접근과 캐스팅 책임까지 가진다
- 시그니처에서 현재 사용자 의존이 드러나지 않는다
- Spring 공식 문서도 이 패턴보다 @AuthenticationPrincipal 쪽을 권장 예시로 보여 준다.

### 예시 2. application이 Spring Security 타입을 직접 받는다

```java
@Service
public class BadChangePasswordService {

    public void change(Authentication authentication, String currentPassword, String newPassword) {
        String userId = ((CustomUserPrincipal) authentication.getPrincipal()).getUserId();
        // ...
    }
}
```

**나쁜 이유:**

- application이 Spring Security에 결합된다
- 유스케이스 입력이 보안 프레임워크 타입에 종속된다
- 테스트와 재사용성이 나빠진다

### 예시 3. controller가 role check로 인가를 직접 처리한다

```java
@RestController
@RequestMapping("/api/v1/admin")
public class BadAdminController {

    @PostMapping("/users/{userId}/lock")
    public ApiResult<Void> lock(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable String userId
    ) {
        if (!currentUser.authorities().contains("ROLE_ADMIN")) {
            throw new AccessDeniedException("forbidden");
        }

        // ...
        return ApiResult.success(null);
    }
}
```

**나쁜 이유:**

- 인가 규칙이 controller imperative code로 새어 나갔다
- security rule/method security와 역할이 충돌한다
- defense in depth 구조가 흐려진다.

### 예시 4. JWT claim을 여러 계층에서 직접 파싱한다

```java
@Service
public class BadUserService {

    public void doSomething(JwtAuthenticationToken authentication) {
        String userId = authentication.getToken().getClaimAsString("sub");
        // ...
    }
}
```

**나쁜 이유:**

- application이 특정 인증 메커니즘(JWT claim 구조)에 결합된다
- principal 해석 책임이 security adapter에 모이지 않는다
- 토큰 구조 변경이 여러 계층으로 번진다
