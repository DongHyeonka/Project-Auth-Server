# Response Format 예시

## 좋은 예시

### 예시 1. 일반 성공 응답은 ApiResult<T>로 반환한다

```java
public record UserResponse(
        String userId,
        String email,
        String displayName
) {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserQueryController {

    private final UserQueryUseCase userQueryUseCase;

    @GetMapping("/{userId}")
    public ApiResult<UserResponse> getUser(@PathVariable String userId) {
        UserResult result = userQueryUseCase.getUser(userId);

        return ApiResult.success(new UserResponse(
                result.userId(),
                result.email(),
                result.displayName()
        ));
    }
}
```

**좋은 이유:**

- 성공 응답 형식이 명확하다
- business payload와 공통 envelope가 분리된다
- controller가 임시 JSON을 조립하지 않는다

### 예시 2. HTTP 제어가 필요할 때만 ResponseEntity<ApiResult<T>>를 사용한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserCommandController {

    private final RegisterUserUseCase registerUserUseCase;

    @PostMapping
    public ResponseEntity<ApiResult<CreateUserResponse>> register(
            @Valid @RequestBody CreateUserRequest request
    ) {
        RegisteredUser result = registerUserUseCase.register(
                request.email(),
                request.password(),
                request.displayName()
        );

        CreateUserResponse response = new CreateUserResponse(
                result.userId(),
                result.email(),
                result.displayName()
        );

        return ResponseEntity.created(URI.create("/api/users/" + response.userId()))
                .body(ApiResult.success(response));
    }
}
```

**좋은 이유:**

- ResponseEntity 사용 이유가 201 Created + Location으로 분명하다
- body 형식은 여전히 프로젝트 표준을 따른다

### 예시 3. 실패 응답은 advice에서 ApiResult로 통일한다

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        FieldError::getField,
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (first, second) -> first
                ));

        return ResponseEntity.badRequest()
                .body(ApiResult.fail(ErrorCode.REQUEST_VALIDATION_FAILED, errors));
    }
}
```

**좋은 이유:**

- 예외 응답 형식이 중앙에서 통일된다
- controller가 실패 body를 직접 조립하지 않는다
- 상세 오류 정보가 규칙적으로 담긴다

### 예시 4. 전역 응답 래핑은 이중 래핑을 피한다

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

- 공통 envelope 적용 지점이 분명하다
- 이미 래핑된 응답을 다시 감싸지 않는다
- file/resource 응답을 무심코 건드리지 않는다

## 나쁜 예시

### 예시 1. controller마다 임시 응답 구조를 만든다

```java
@GetMapping("/api/users/{userId}")
public Map<String, Object> getUser(@PathVariable String userId) {
    UserResult result = userQueryUseCase.getUser(userId);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("ok", true);
    response.put("payload", result);
    return response;
}
```

**나쁜 이유:**

- 프로젝트 공통 응답 형식을 깨뜨린다
- 다른 endpoint와 구조가 달라진다
- 임시 필드명이 계약이 되어 버린다

### 예시 2. ResponseEntity를 의미 없이 남발한다

```java
@GetMapping("/api/health")
public ResponseEntity<ApiResult<String>> health() {
    return ResponseEntity.ok(ApiResult.success("ok"));
}
```

**나쁜 이유:**

- 별도 header/status 제어가 없다
- 불필요한 ceremony만 늘어난다

### 예시 3. 실패 응답에 내부 예외 메시지를 그대로 노출한다

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResult<Void>> handle(Exception ex) {
    return ResponseEntity.internalServerError()
            .body(ApiResult.fail("INTERNAL_SERVER_ERROR", ex.getMessage()));
}
```

**나쁜 이유:**

- 내부 메시지가 외부 계약이 된다
- 민감한 구현 세부사항이 노출될 수 있다
- 외부 응답 메시지 정책이 없다

### 예시 4. ResponseBodyAdvice에서 무조건 감싼다

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

- 이미 ApiResult인 응답도 이중 래핑한다
- 파일/리소스/스트리밍 응답을 망가뜨릴 수 있다
- 규약 적용이 아니라 무차별 변환이 된다
