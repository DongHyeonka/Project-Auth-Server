# Idempotency 예시

## 좋은 예시

### 예시 1. 멱등 키가 필요한 POST endpoint는 헤더를 명시적으로 받는다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserCommandController {

    private final RegisterUserUseCase registerUserUseCase;

    @PostMapping
    public ResponseEntity<ApiResult<CreateUserResponse>> register(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateUserRequest request,
            AuthenticatedUser authenticatedUser
    ) {
        CreateUserCommand command = new CreateUserCommand(
                authenticatedUser.userId(),
                idempotencyKey,
                request.email(),
                request.password(),
                request.displayName()
        );

        CreateUserResult result = registerUserUseCase.register(command);

        return ResponseEntity.created(URI.create("/api/v1/users/" + result.userId()))
                .body(ApiResult.success(new CreateUserResponse(
                        result.userId(),
                        result.email(),
                        result.displayName()
                )));
    }
}
```

**좋은 이유:**

- controller는 헤더를 읽고 command로 전달만 한다
- 멱등성 구현 책임이 controller에 머무르지 않는다
- POST 생성 endpoint에서 멱등 키 요구가 명확하다

### 예시 2. application/service에서 키 + fingerprint로 중복을 판정한다

```java
public record IdempotencyScope(
        String actorId,
        String operation
) {
}

public record StoredRegistrationResult(
        String userId,
        String email,
        String displayName
) {
}

public interface IdempotencyStore {
    Optional<StoredRegistrationResult> findCompleted(
            IdempotencyScope scope,
            String key,
            String fingerprint
    );

    IdempotencyStartResult tryStart(
            IdempotencyScope scope,
            String key,
            String fingerprint
    );

    void complete(
            IdempotencyScope scope,
            String key,
            String fingerprint,
            StoredRegistrationResult result
    );
}

@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

    private final IdempotencyStore idempotencyStore;
    private final UserRegistrationService userRegistrationService;

    public CreateUserResult register(CreateUserCommand command) {
        IdempotencyScope scope = new IdempotencyScope(command.actorId(), "register-user");
        String fingerprint = fingerprint(command);

        idempotencyStore.findCompleted(scope, command.idempotencyKey(), fingerprint)
                .ifPresent(storedResponse -> {
                    throw new IdempotentReplayException(storedResponse);
                });

        IdempotencyStartResult startResult = idempotencyStore.tryStart(
                scope,
                command.idempotencyKey(),
                fingerprint
        );

        if (startResult == IdempotencyStartResult.IN_PROGRESS) {
            throw new IdempotencyRequestInProgressException();
        }

        if (startResult == IdempotencyStartResult.KEY_REUSED_WITH_DIFFERENT_REQUEST) {
            throw new IdempotencyKeyMismatchException();
        }

        CreateUserResult result = userRegistrationService.register(command);

        StoredRegistrationResult storedResult = new StoredRegistrationResult(
                result.userId(),
                result.email(),
                result.displayName()
        );

        idempotencyStore.complete(scope, command.idempotencyKey(), fingerprint, storedResult);

        return result;
    }

    private String fingerprint(CreateUserCommand command) {
        return DigestUtils.sha256Hex(
                command.actorId() + "|" +
                command.email() + "|" +
                command.displayName()
        );
    }
}
```

**좋은 이유:**

- 멱등성 판정이 application 경계에 있다
- key뿐 아니라 fingerprint도 비교한다
- 완료 결과 재생, 진행 중 충돌, key 재사용 충돌을 분리한다
- application은 HTTP status, ApiResult, JSON 직렬화 세부를 알지 않는다

### 예시 3. 멱등성 오류도 공통 에러 응답 규약으로 처리한다

```java
@RestControllerAdvice
public class IdempotencyExceptionHandler {

    @ExceptionHandler(IdempotencyKeyMissingException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingKey() {
        ErrorCode errorCode = ErrorCode.IDEMPOTENCY_KEY_REQUIRED;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode));
    }

    @ExceptionHandler(IdempotencyRequestInProgressException.class)
    public ResponseEntity<ApiResult<Void>> handleInProgress() {
        ErrorCode errorCode = ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode));
    }

    @ExceptionHandler(IdempotencyKeyMismatchException.class)
    public ResponseEntity<ApiResult<Void>> handleMismatch() {
        ErrorCode errorCode = ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode));
    }
}
```

**좋은 이유:**

- 400/409/422 같은 HTTP status와 application error code를 함께 유지한다
- 멱등성 오류도 전체 API 에러 규약에 맞춰진다

### 예시 4. key는 opaque UUID를 사용한다

요청 예:

```http
POST /api/v1/users
Idempotency-Key: 8e03978e-40d5-43e8-bc93-6894a57f9324
Content-Type: application/json
```

**좋은 이유:**

- 민감정보가 없다
- 재시도 시 같은 key를 다시 보낼 수 있다
- 운영/추적에도 적당한 opaque identifier다

## 나쁜 예시

### 예시 1. GET에 멱등 키를 요구한다

```java
@GetMapping("/{userId}")
public ApiResult<UserResponse> getUser(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable String userId
) {
    ...
}
```

**나쁜 이유:**

- GET은 이미 safe/idempotent다
- 불필요한 계약 복잡도만 늘어난다

### 예시 2. 서버가 멱등 키를 생성한다

```java
@PostMapping
public ApiResult<CreateUserResponse> register(@RequestBody CreateUserRequest request) {
    String idempotencyKey = UUID.randomUUID().toString();
    ...
}
```

**나쁜 이유:**

- client가 타임아웃 후 같은 요청을 재시도할 때 같은 key를 다시 보낼 수 없다
- 재시도 안전성이라는 목적을 달성하지 못한다

### 예시 3. 이메일을 멱등 키로 사용한다

```http
Idempotency-Key: donghyun@example.com
```

**나쁜 이유:**

- 개인정보가 key에 노출된다
- 요청 의도 식별자와 사용자 식별자가 뒤섞인다
- 같은 사용자의 다른 요청을 구분하기 어렵다

### 예시 4. controller의 로컬 메모리로만 중복을 막는다

```java
@RestController
@RequestMapping("/api/v1/users")
public class BadUserController {

    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    @PostMapping
    public ApiResult<Void> register(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateUserRequest request
    ) {
        if (!processedKeys.add(idempotencyKey)) {
            return ApiResult.success(null);
        }

        // 실제 생성 처리
        return ApiResult.success(null);
    }
}
```

**나쁜 이유:**

- 다중 인스턴스 환경에서 깨진다
- fingerprint 비교가 없다
- 애플리케이션 재기동 시 기록이 사라진다
- controller가 멱등성 구현 책임까지 떠안는다

### 예시 5. 같은 key를 다른 payload에 재사용해도 새 요청으로 처리한다

```java
public void handle(String key, CreateUserRequest request) {
    if (store.contains(key)) {
        process(request); // 그냥 다시 처리
        return;
    }
    process(request);
}
```

**나쁜 이유:**

- key 재사용 오용을 막지 못한다
- 중복 생성/중복 실행 위험이 남는다
- “같은 요청의 재시도”와 “다른 요청”을 구분하지 못한다
