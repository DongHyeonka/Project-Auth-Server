# DTO / Domain / Entity separation 예시

## 좋은 예시 1: request DTO -> command -> domain

```java
public record CreateUserRequest(
        String email,
        String password,
        String name
) {}

public record CreateUserCommand(
        String email,
        String password,
        String name
) {}

public final class UserWebMapper {

    public CreateUserCommand toCommand(CreateUserRequest request) {
        return new CreateUserCommand(
                request.email(),
                request.password(),
                request.name()
        );
    }
}
```

**왜 좋은가:**

- 웹 입력 모델과 application 입력 모델이 분리된다
- request binding과 business 의미 부여 경계가 생긴다

## 좋은 예시 2: entity -> domain 분리

```java
@Entity
@Table(name = "users")
public class UserJpaEntity {
    @Id
    private Long id;
    private String email;
    private String encodedPassword;
    private String name;
    protected UserJpaEntity() {}
}

public class User {
    private final UserId id;
    private final UserEmail email;
    private final UserName name;
    private final EncodedPassword password;

    private User(...) { ... }
}
```

**왜 좋은가:**

- JPA 제약과 도메인 의미가 분리된다
- domain이 persistence annotation에 오염되지 않는다

## 좋은 예시 3: domain -> response DTO 분리

```java
public record UserResponse(
        Long id,
        String email,
        String name
) {}

public final class UserResponseMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.id().value(),
                user.email().value(),
                user.name().value()
        );
    }
}
```

**왜 좋은가:**

- 외부 응답 계약이 명시적이다
- domain 전체를 그대로 노출하지 않는다

## 나쁜 예시 1: entity를 바로 response로 반환

```java
@GetMapping("/{id}")
public UserJpaEntity getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**문제:**

- persistence 구조가 외부 계약으로 새어 나간다
- 민감정보/지연로딩/관계 구조가 노출될 수 있다
- API와 persistence가 강하게 결합된다

## 나쁜 예시 2: request DTO를 그대로 domain으로 사용

```java
public User createUser(CreateUserRequest request) {
    return userService.create(request);
}
```

**문제:**

- 웹 입력 모델이 business layer로 직접 흘러간다
- validation/binding shape가 domain/application 설계를 오염시킨다

## 나쁜 예시 3: domain에 JPA/JSON/validation annotation 혼합

```java
@Entity
public class User {

    @Id
    private Long id;

    @JsonProperty("email")
    @NotBlank
    private String email;
}
```

**문제:**

- persistence / serialization / validation / business 의미가 한 타입에 섞인다
- 변경 이유가 서로 다른 관심사가 강결합된다
