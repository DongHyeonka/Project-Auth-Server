# Validation Location 예시

## 좋은 예시

### 예시 1. request DTO 구조 검증은 presentation에서 처리한다

```java
public record CreateSessionRequest(
        @NotBlank String email,
        @NotBlank String password,
        @NotNull LoginType loginType
) {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sessions")
public class SessionCommandController {

    private final CreateSessionUseCase createSessionUseCase;

    @PostMapping
    public ApiResult<CreateSessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        CreateSessionResponse response = createSessionUseCase.create(
                request.email(),
                request.password(),
                request.loginType()
        );
        return ApiResult.success(response);
    }
}
```

**좋은 이유:**

- request shape 검증이 web boundary에 있다
- controller는 transport DTO를 domain object와 분리한다
- business rule 판단은 use case로 넘긴다

### 예시 2. path variable / request param 제약은 메서드 파라미터에 직접 둔다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserQueryController {

    private final UserQueryUseCase userQueryUseCase;

    @GetMapping("/{userId}")
    public ApiResult<UserResponse> getUser(
            @PathVariable @NotBlank String userId,
            @RequestParam(defaultValue = "1") @Min(1) int page
    ) {
        UserResponse response = userQueryUseCase.getUser(userId, page);
        return ApiResult.success(response);
    }
}
```

**좋은 이유:**

- scalar input 제약이 controller boundary에 명확히 드러난다
- request DTO가 필요 없는 단순 입력을 과하게 감싸지 않는다

### 예시 3. application은 조회가 필요한 정책 검증을 담당한다

```java
@Service
@RequiredArgsConstructor
public class CreateSessionUseCase {

    private final UserRepository userRepository;

    public CreateSessionResponse create(CreateSessionCommand command) {
        if (!userRepository.existsActiveUserByEmail(command.email())) {
            throw new UserNotFoundException(command.email());
        }

        if (command.loginType() == LoginType.PASSWORDLESS
                && command.credential() instanceof PasswordCredential) {
            throw new InvalidLoginRequestException();
        }

        // 실제 세션 생성
        return new CreateSessionResponse(...);
    }
}

public record CreateSessionCommand(
        String email,
        LoginCredential credential,
        LoginType loginType
) {
    public CreateSessionCommand {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(credential, "credential must not be null");
        Objects.requireNonNull(loginType, "loginType must not be null");
    }
}

public sealed interface LoginCredential permits PasswordCredential, PasswordlessCredential {
}

public record PasswordCredential(String value) implements LoginCredential {
    public PasswordCredential {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new InvalidLoginRequestException();
        }
    }
}

public record PasswordlessCredential() implements LoginCredential {
}
```

**좋은 이유:**

- DB 조회가 필요한 규칙을 controller validation에 두지 않았다
- use case 전제조건 검증이 application에 있다
- nullable password를 application 내부로 전파하지 않고 명시적 credential 타입으로 표현한다

### 예시 4. domain은 자기 불변식을 스스로 보장한다

```java
public final class Email {

    private final String value;

    private Email(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException("Email must not be blank");
        }
        if (!value.contains("@")) {
            throw new DomainRuleViolationException("Email format is invalid");
        }
        this.value = value;
    }

    public static Email of(String value) {
        return new Email(value);
    }

    public String value() {
        return value;
    }
}
```

**좋은 이유:**

- domain invariant를 controller에 의존하지 않는다
- 어디서 생성되더라도 유효한 상태만 허용한다

### 예시 5. web 전용 복잡한 입력 검증은 @InitBinder + custom Validator로 제한적으로 둔다

```java
public class ChangePasswordRequestValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return ChangePasswordRequest.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ChangePasswordRequest request = (ChangePasswordRequest) target;

        if (request.newPassword() != null
                && request.newPasswordConfirm() != null
                && !request.newPassword().equals(request.newPasswordConfirm())) {
            errors.rejectValue("newPasswordConfirm", "password.confirm.mismatch");
        }
    }
}

@RestController
@RequestMapping("/api/password")
public class PasswordController {

    @InitBinder("changePasswordRequest")
    void initBinder(WebDataBinder binder) {
        binder.addValidators(new ChangePasswordRequestValidator());
    }

    @PostMapping("/change")
    public ApiResult<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest
    ) {
        return ApiResult.success(null);
    }
}
```

**좋은 이유:**

- request-object 내부의 web 입력 규칙만 binder validator에 둔다
- business rule 전체를 validator에 몰아넣지 않는다

## 나쁜 예시

### 예시 1. entity를 request binding 대상으로 직접 노출한다

```java
@Entity
public class User {
    @Id
    private Long id;
    private String email;
    private String role;
}

@PostMapping("/users")
public ApiResult<Void> create(@Valid @RequestBody User user) {
    ...
}
```

**나쁜 이유:**

- web input model과 domain/persistence model이 섞였다
- 바인딩 범위가 불필요하게 넓다
- request schema 변경이 domain/persistence 모델에 직접 번진다

### 예시 2. controller validation만 믿고 domain에서 아무 것도 보장하지 않는다

```java
public final class Email {

    private final String value;

    public Email(String value) {
        this.value = value;
    }
}
```

**나쁜 이유:**

- 다른 진입 경로에서 잘못된 값이 들어오면 막지 못한다
- domain이 자기 불변식을 보장하지 못한다

### 예시 3. controller 클래스에 @Validated를 붙여 구식 proxy 방식에 기대한다

```java
@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{userId}")
    public ApiResult<UserResponse> get(@PathVariable @NotBlank String userId) {
        ...
    }
}
```

**나쁜 이유:**

- Spring MVC 6.1+ built-in method validation 대신 class-level AOP proxy 경로로 흐를 수 있다
- 이 프로젝트의 controller 규칙과 맞지 않는다

### 예시 4. filter / interceptor에서 business validation을 수행한다

```java
@Component
@RequiredArgsConstructor
public class BadLoginValidationFilter extends OncePerRequestFilter {

    private final LoginPolicyService loginPolicyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        loginPolicyService.validateLoginWindow();
        filterChain.doFilter(request, response);
    }
}
```

**나쁜 이유:**

- business validation이 web infrastructure 훅으로 새어 나갔다
- 요청 바운더리 검증과 use case 규칙이 섞였다

### 예시 5. service method validation만 믿고 복잡한 정책을 숨긴다

```java
@Service
@Validated
public class BadCreateSessionService {

    public void create(
            @NotBlank String email,
            @NotBlank String password,
            @NotNull LoginType loginType
    ) {
        // 복잡한 도메인 정책을 전부 메서드 시그니처 제약에 기대함
    }
}
```

**나쁜 이유:**

- method validation은 보조 수단이지 핵심 정책 엔진이 아니다
- proxy 기반 동작 특성 때문에 경계가 흐려질 수 있다
- business rule이 시그니처 제약 뒤에 숨어 버린다
