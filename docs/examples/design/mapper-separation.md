# mapper separation 예시

## 좋은 예시 1: web request -> command 매핑

```java
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

- HTTP request 구조를 application command 구조로만 번역한다
- 비즈니스 정책을 결정하지 않는다

## 좋은 예시 2: domain -> response DTO 매핑

```java
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

- 응답 계약만 만든다
- repository/service 호출이 없다

## 좋은 예시 3: persistence entity -> domain 매핑 분리

```java
public final class UserPersistenceMapper {

    public User toDomain(UserJpaEntity entity) {
        return User.restore(
                entity.getId(),
                entity.getEmail(),
                entity.getName(),
                entity.getEncodedPassword()
        );
    }

    public UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.id().value(),
                user.email().value(),
                user.name().value(),
                user.password().encodedValue()
        );
    }
}
```

**왜 좋은가:**

- persistence 구조와 domain 구조를 별도 경계에서 번역한다
- JPA 세부가 domain으로 직접 새지 않는다

## 좋은 예시 4: update mapping을 명시적으로 분리

```java
public interface UserPersistenceMapper {

    UserJpaEntity toNewEntity(User user);

    void updateEntity(User user, UserJpaEntity target);
}
```

**왜 좋은가:**

- 생성과 수정의 계약이 다름을 드러낸다
- side effect가 있는 매핑을 명시한다

## 나쁜 예시 1: 매퍼에서 repository 호출

```java
public final class UserMapper {

    private final RoleRepository roleRepository;

    public User toDomain(UserRequest request) {
        Role role = roleRepository.findByName(request.roleName()).orElseThrow();
        return User.create(request.email(), role);
    }
}
```

**문제:**

- 매퍼가 번역을 넘어 DB 조회까지 한다
- 테스트와 책임 분리가 어려워진다

**개선:**

- 호출자가 Role을 먼저 준비해서 전달한다

## 나쁜 예시 2: 매퍼에서 비즈니스 규칙 결정

```java
public UserStatus toStatus(UserRequest request) {
    if (request.provider().equals("google")) {
        return UserStatus.ACTIVE;
    }
    return UserStatus.PENDING;
}
```

**문제:**

- 상태 결정 정책이 매퍼에 숨어 있다
- 단순 구조 변환이 아니라 비즈니스 의미를 만든다

**개선:**

- status 결정은 application/domain 정책으로 이동

## 나쁜 예시 3: web + persistence + integration를 한 매퍼에 몰아넣기

```java
public final class UserMapper {
    CreateUserCommand toCommand(CreateUserRequest request) { ... }
    UserJpaEntity toEntity(User user) { ... }
    ExternalUserPayload toPayload(User user) { ... }
}
```

**문제:**

- 경계가 섞인다
- 변경 이유가 달라 함께 진화하기 어렵다

**개선:**

- UserWebMapper
- UserPersistenceMapper
- UserExternalMapper
- 로 분리
