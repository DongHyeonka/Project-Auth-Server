# Request / Response DTO 예시

## 좋은 예시

### 예시 1. request와 response를 명확히 분리한다

```java
public record CreateUserRequest(
        @NotBlank String email,
        @NotBlank String password,
        @NotBlank String displayName
) {
}

public record CreateUserResponse(
        String userId,
        String email,
        String displayName
) {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserCommandController {

    private final RegisterUserUseCase registerUserUseCase;

    @PostMapping
    public ApiResult<CreateUserResponse> register(@Valid @RequestBody CreateUserRequest request) {
        RegisteredUser registeredUser = registerUserUseCase.register(
                request.email(),
                request.password(),
                request.displayName()
        );

        return ApiResult.success(new CreateUserResponse(
                registeredUser.userId(),
                registeredUser.email(),
                registeredUser.displayName()
        ));
    }
}
```

**좋은 이유:**

- request와 response 역할이 분리된다
- request DTO가 그대로 내부 모델처럼 전파되지 않는다
- 응답이 entity 구조가 아니라 API 계약 중심으로 표현된다

### 예시 2. query/form 입력은 전용 @ModelAttribute DTO로 받는다

```java
public record UserSearchRequest(
        @NotBlank String keyword,
        @Min(1) int page,
        @Min(1) @Max(100) int size
) {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserQueryController {

    private final SearchUsersUseCase searchUsersUseCase;

    @GetMapping
    public ApiResult<UserSearchResponse> search(@Valid @ModelAttribute UserSearchRequest request) {
        UserSearchResult result = searchUsersUseCase.search(
                request.keyword(),
                request.page(),
                request.size()
        );

        return ApiResult.success(UserSearchResponse.from(result));
    }
}
```

**좋은 이유:**

- query 입력도 전용 web model로 분리된다
- @ModelAttribute 대상이 domain/entity가 아니다
- 검색 조건과 응답 모델이 분리된다

### 예시 3. ResponseEntity는 HTTP 제어가 필요할 때만 사용한다

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
        RegisteredUser registeredUser = registerUserUseCase.register(
                request.email(),
                request.password(),
                request.displayName()
        );

        CreateUserResponse response = new CreateUserResponse(
                registeredUser.userId(),
                registeredUser.email(),
                registeredUser.displayName()
        );

        return ResponseEntity.created(URI.create("/api/users/" + response.userId()))
                .body(ApiResult.success(response));
    }
}
```

**좋은 이유:**

- 201 Created와 Location 제어가 필요해 ResponseEntity 사용 이유가 분명하다

## 나쁜 예시

### 예시 1. entity를 request body로 직접 받는다

```java
@Entity
public class User {
    @Id
    private Long id;
    private String email;
    private String role;
}

@PostMapping("/api/users")
public ApiResult<Void> create(@Valid @RequestBody User user) {
    ...
}
```

**나쁜 이유:**

- web input model과 persistence/domain model이 섞인다
- 바인딩 범위가 불필요하게 넓다
- API 변경이 entity 구조에 직접 번진다

### 예시 2. request와 response를 하나의 DTO로 재사용한다

```java
public record UserDto(
        String userId,
        String email,
        String password,
        String displayName,
        String role
) {
}

@PostMapping("/api/users")
public ApiResult<UserDto> create(@RequestBody UserDto request) {
    ...
}
```

**나쁜 이유:**

- 요청과 응답의 책임이 섞인다
- 응답에 불필요하거나 민감한 필드가 섞이기 쉽다
- write model과 read model이 분리되지 않는다

### 예시 3. DTO를 내부 모델처럼 그대로 넘긴다

```java
@PostMapping("/api/users")
public ApiResult<Void> create(@Valid @RequestBody CreateUserRequest request) {
    registerUserUseCase.register(request);
    return ApiResult.success(null);
}
```

**나쁜 이유:**

- request DTO가 presentation 경계를 넘어 application 시그니처로 새어 나간다
- 내부 use case가 transport model에 결합된다

### 예시 4. 응답으로 entity를 직접 반환한다

```java
@GetMapping("/api/users/{userId}")
public ApiResult<User> get(@PathVariable Long userId) {
    User user = userRepository.findById(userId).orElseThrow();
    return ApiResult.success(user);
}
```

**나쁜 이유:**

- persistence/domain 구조가 외부 계약이 된다
- 내부 필드가 의도치 않게 노출되기 쉽다
- controller가 repository와 entity에 직접 결합된다
