# API Controller 예시

## 좋은 예시

### 예시 1. controller는 request DTO를 받아 use case를 호출하고 표준 응답을 반환한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionCommandController {

    private final CreateSessionUseCase createSessionUseCase;

    @PostMapping
    public ApiResult<CreateSessionResponse> create(
            @Valid @RequestBody CreateSessionRequest request
    ) {
        CreateSessionResult result = createSessionUseCase.create(
                request.email(),
                request.password(),
                request.loginType()
        );

        return ApiResult.success(CreateSessionResponse.from(result));
    }
}
```

**좋은 이유:**

- @RestController가 API 용도와 맞다
- JSON body를 전용 request DTO로 받는다
- controller가 use case 호출과 응답 반환에 집중한다

### 예시 2. ResponseEntity는 HTTP 제어가 필요할 때만 사용한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserCommandController {

    private final RegisterUserUseCase registerUserUseCase;

    @PostMapping
    public ResponseEntity<ApiResult<UserCreatedResponse>> register(
            @Valid @RequestBody RegisterUserRequest request
    ) {
        UserCreatedResult result = registerUserUseCase.register(request.email(), request.password());
        UserCreatedResponse response = UserCreatedResponse.from(result);

        URI location = URI.create("/api/users/" + response.userId());

        return ResponseEntity.created(location)
                .body(ApiResult.success(response));
    }
}
```

**좋은 이유:**

- 201 Created와 Location 헤더가 필요한 경우에만 ResponseEntity를 사용한다
- 모든 endpoint를 습관적으로 ResponseEntity로 감싸지 않는다

### 예시 3. 입력 출처를 시그니처에 명시한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserQueryController {

    private final UserQueryUseCase userQueryUseCase;

    @GetMapping("/{userId}")
    public ApiResult<UserResponse> getUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        UserResult result = userQueryUseCase.getUser(userId, includeInactive);
        return ApiResult.success(UserResponse.from(result));
    }
}
```

**좋은 이유:**

- path와 query 입력 출처가 시그니처에서 구분된다
- HttpServletRequest 전체를 들고 오지 않아도 되는 입력은 annotation으로 처리한다
- request id 같은 관측용 헤더는 use case 입력으로 섞지 않는다

### 예시 4. controller는 예외를 직접 잡지 않는다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/password")
public class PasswordController {

    private final ChangePasswordUseCase changePasswordUseCase;

    @PostMapping("/change")
    public ApiResult<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        changePasswordUseCase.change(
                request.userId(),
                request.currentPassword(),
                request.newPassword()
        );

        return ApiResult.success(null);
    }
}
```

**좋은 이유:**

- 예외는 @RestControllerAdvice에서 통합 처리할 수 있다
- controller가 공통 에러 응답 정책을 직접 품지 않는다

## 나쁜 예시

### 예시 1. controller가 repository를 직접 호출한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class BadUserController {

    private final UserRepository userRepository;

    @GetMapping("/{userId}")
    public ApiResult<User> getUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return ApiResult.success(user);
    }
}
```

**나쁜 이유:**

- controller가 persistence access를 직접 수행한다
- domain/entity가 외부 응답 모델로 직접 노출된다
- application boundary가 사라진다

### 예시 2. entity를 request body로 직접 받는다

```java
@RestController
@RequestMapping("/api/users")
public class BadUserCommandController {

    @PostMapping
    public ApiResult<Void> create(@Valid @RequestBody User user) {
        return ApiResult.success(null);
    }
}
```

**나쁜 이유:**

- request model과 domain/persistence model이 섞인다
- 웹 입력 변경이 domain/entity 구조에 직접 번진다

### 예시 3. 모든 응답을 습관적으로 ResponseEntity로 감싼다

```java
@RestController
@RequestMapping("/api/health")
public class BadHealthController {

    @GetMapping
    public ResponseEntity<ApiResult<String>> health() {
        return ResponseEntity.ok(ApiResult.success("ok"));
    }
}
```

**나쁜 이유:**

- 추가로 제어할 status/header가 없다
- 불필요한 ceremony만 늘어난다

### 예시 4. controller 안에서 공통 예외를 직접 처리한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class BadSessionController {

    private final CreateSessionUseCase createSessionUseCase;

    @PostMapping
    public ResponseEntity<ApiResult<?>> create(@RequestBody CreateSessionRequest request) {
        try {
            return ResponseEntity.ok(ApiResult.success(
                    createSessionUseCase.create(request.email(), request.password(), request.loginType())
            ));
        } catch (InvalidCredentialException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResult.fail(ErrorCode.INVALID_CREDENTIAL));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResult.fail(ErrorCode.INTERNAL_SERVER_ERROR));
        }
    }
}
```

**나쁜 이유:**

- controller마다 예외 정책이 중복된다
- 전역 advice 기준과 충돌한다
- 정상 흐름과 에러 흐름이 한 메서드에 뒤섞인다
