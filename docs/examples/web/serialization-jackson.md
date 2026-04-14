# Serialization / Jackson 예시

## 좋은 예시

### 예시 1. request/response DTO를 분리하고 timestamp는 offset 기반으로 노출한다

```java
public record CreateSessionRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}

public record CreateSessionResponse(
        String sessionId,
        String accessToken,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt
) {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
public class SessionCommandController {

    private final CreateSessionUseCase createSessionUseCase;

    @PostMapping
    public ApiResult<CreateSessionResponse> create(
            @Valid @RequestBody CreateSessionRequest request
    ) {
        SessionResult result = createSessionUseCase.create(request.email(), request.password());

        return ApiResult.success(new CreateSessionResponse(
                result.sessionId(),
                result.accessToken(),
                result.issuedAt(),
                result.expiresAt()
        ));
    }
}
```

**좋은 이유:**

- request/response 계약이 분리된다
- password는 응답 DTO에 존재하지 않는다
- timestamp가 OffsetDateTime으로 명확하다

### 예시 2. 외부 공급자 webhook DTO만 lenient하게 받는다

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalAuthWebhookRequest(
        String eventId,
        String eventType,
        String subjectId
) {
}
```

**좋은 이유:**

- 외부 공급자가 필드를 추가해도 파싱이 덜 깨진다
- lenient 정책이 third-party integration DTO로 국소화된다
- first-party API request DTO와 기준이 분리된다

### 예시 3. 외부 계약 이름 mismatch만 @JsonProperty로 보정한다

```java
public record ExternalUserResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("display_name") String displayName
) {
}
```

**좋은 이유:**

- 내부 표준 naming을 전체 프로젝트에 퍼뜨리지 않는다
- mismatch를 DTO 경계에서 해결한다

### 예시 4. write-only 필드는 예외적으로만 사용한다

```java
public record ResetPasswordCommandRequest(
        @NotBlank String userId,
        @NotBlank @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String newPassword
) {
}
```

**좋은 이유:**

- 입력만 받고 다시 내보내면 안 되는 필드를 제한적으로 막는다
- 그래도 request DTO 안에 국소화돼 있다

### 예시 5. 공통 직렬화 예외는 전역 컴포넌트로 등록한다

```java
@Configuration
public class JacksonConfig {

    @Bean
    Module userIdModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(UserId.class, new JsonSerializer<>() {
            @Override
            public void serialize(UserId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.value());
            }
        });
        return module;
    }
}
```

**좋은 이유:**

- 반복되는 값 객체 직렬화를 전역 정책으로 올린다
- controller나 DTO마다 같은 로직을 복붙하지 않는다

## 나쁜 예시

### 예시 1. entity를 그대로 응답으로 내보낸다

```java
@Entity
public class User {
    @Id
    private Long id;
    private String email;
    private String password;
    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;
}

@GetMapping("/api/v1/users/{id}")
public ApiResult<User> getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return ApiResult.success(user);
}
```

**나쁜 이유:**

- persistence 모델이 외부 계약이 된다
- 민감 필드와 lazy relation 노출 위험이 있다
- API shape가 entity 구조에 끌려간다

### 예시 2. controller에서 로컬 ObjectMapper를 만든다

```java
@GetMapping("/api/v1/users/{id}")
public String getUser(@PathVariable Long id) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(userService.getUser(id));
}
```

**나쁜 이유:**

- 전역 Jackson 규칙을 우회한다
- converter, module, naming, time 설정이 끊어진다
- controller 책임이 과도해진다

### 예시 3. public API shape를 @JsonView로 관리한다

```java
public class UserViewModel {

    @JsonView(Summary.class)
    private String userId;

    @JsonView(Summary.class)
    private String displayName;

    @JsonView(Detail.class)
    private String email;

    interface Summary {}
    interface Detail extends Summary {}
}

@GetMapping("/api/v1/users/{id}")
@JsonView(UserViewModel.Summary.class)
public UserViewModel getUser(@PathVariable String id) {
    ...
}
```

**나쁜 이유:**

- summary/detail 계약이 DTO 분리 대신 view 규칙에 숨어든다
- public API contract evolution이 읽기 어려워진다
- versioning/응답 shape 관리 수단으로는 과도하게 간접적이다

### 예시 4. first-party request DTO에서 unknown field를 무비판적으로 무시한다

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateUserRequest(
        String email,
        String password,
        String displayName
) {
}
```

**나쁜 이유:**

- 클라이언트 오타나 잘못된 필드 전송을 조용히 숨길 수 있다
- 우리가 소유한 API 계약이 흐려진다
- strict 정책을 택한 API군과 충돌한다

### 예시 5. null omission을 보기 좋다는 이유만으로 남발한다

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        String userId,
        String displayName,
        String email,
        String phoneNumber
) {
}
```

**나쁜 이유:**

- 필드 omission이 계약 의미를 바꾼다
- 클라이언트가 null과 absent를 구분해야 하는 경우 혼란이 생긴다
- 전역/DTO별 정책이 뒤섞이기 쉽다
