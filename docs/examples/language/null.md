# null 처리 예시

이 문서는 [null 처리 기준](../../standards/language/null.md)을 코드 예시로 확인하기 위한 자료입니다.  
핵심 기준은 `null`을 경계에서만 제한적으로 받고, 내부 로직에 들어가기 전에 non-null 값이나 명시적 상태로 정리하는 것입니다.

## 좋은 예시 1: 생성자/경계에서 즉시 검증

```java
public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
}
```

왜 좋은가:

- boundary에서 non-null 계약을 바로 강제한다.
- 내부 필드는 이후 non-null로 다룰 수 있다.

## 좋은 예시 2: 외부 입력은 DTO에서 받고 내부에서 정리

```java
public CreateUserCommand toCommand(CreateUserRequest request) {
    return new CreateUserCommand(
            UserEmail.from(request.email()),
            request.nickname() == null ? null : request.nickname().trim()
    );
}
```

더 좋은 경우:

- nullable `nickname`을 value object 또는 명시적 규칙으로 바로 정리한다.

왜 좋은가:

- nullable 입력이 boundary에 머문다.
- 내부 의미로 들어가기 전에 정리할 수 있다.

## 좋은 예시 3: 컬렉션은 null 대신 empty 반환

```java
public List<Role> findRoles(Long userId) {
    List<Role> roles = roleRepository.findAllByUserId(userId);
    return roles == null ? List.of() : roles;
}
```

더 좋은 경우:

- repository 계약 자체를 null이 아닌 empty 반환으로 고정한다.

왜 좋은가:

- 호출자가 불필요한 null-check를 하지 않아도 된다.

## 좋은 예시 4: persistence -> domain 변환에서 nullable 차단

```java
public User toDomain(UserJpaEntity entity) {
    return User.restore(
            Objects.requireNonNull(entity.getId(), "id must not be null"),
            UserEmail.from(Objects.requireNonNull(entity.getEmail(), "email must not be null")),
            Objects.requireNonNull(entity.getEncodedPassword(), "encodedPassword must not be null"),
            UserName.from(Objects.requireNonNull(entity.getName(), "name must not be null"))
    );
}
```

왜 좋은가:

- DB nullable/오염 상태를 domain으로 전파하지 않는다.
- invariant 경계가 분명하다.

## 나쁜 예시 1: Optional과 null 혼용

```java
public Optional<User> findByEmail(String email) {
    if (email == null) {
        return null;
    }
    // ...
}
```

문제:

- `Optional` 반환 계약을 깨뜨린다.
- 호출자는 `Optional`과 `null`을 동시에 처리해야 한다.

개선:

- null 입력 자체를 검증한다.
- 또는 `Optional.empty()`를 반환한다.
- 또는 파라미터를 non-null로 강제한다.

## 나쁜 예시 2: null을 business 의미로 사용

```java
if (user.getProvider() == null) {
    // local user
}
```

문제:

- business state가 `null`에 숨는다.
- 의미가 타입으로 드러나지 않는다.

개선:

```java
if (user.getProvider() == AuthProvider.LOCAL) {
    // local user
}
```

또는 명시적 enum/state를 사용한다.

## 나쁜 예시 3: 여러 계층으로 nullable 전파

```java
public String handle(String nickname) {
    return service.process(nickname);
}

public String process(String nickname) {
    return repository.saveNickname(nickname);
}
```

문제:

- nullable 여부가 계약으로 명시되지 않는다.
- 모든 계층이 방어 책임을 떠넘긴다.

개선:

- boundary에서 검증/정규화한다.
- nullable이면 `Optional`, value object, 명시적 command로 변환한다.
