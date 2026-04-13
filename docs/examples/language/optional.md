# Optional 사용 예시

이 문서는 [Optional 사용 기준](../../standards/language/optional.md)을 코드 예시로 확인하기 위한 자료입니다.  
핵심 기준은 `Optional`을 값의 부재가 가능한 단건 반환 타입에 쓰고, 필드/파라미터/직렬화 경계에는 기본적으로 쓰지 않는 것입니다.

## 좋은 예시 1: 단건 조회 결과 없음 표현

```java
public Optional<User> findByEmail(String email) {
    return userRepository.findByEmail(email);
}
```

왜 좋은가:

- 단건 조회 결과의 부재를 반환 타입에서 명시한다.
- 호출자에게 "없을 수 있음"을 강제한다.

## 좋은 예시 2: transform chain

```java
public Optional<String> findActiveUserEmail(Long userId) {
    return userRepository.findById(userId)
            .filter(User::isActive)
            .map(User::getEmail);
}
```

왜 좋은가:

- `isPresent() + get()` 없이 선언적으로 표현한다.
- 값이 없으면 자연스럽게 empty로 전파된다.

## 좋은 예시 3: nested Optional 방지

```java
public Optional<Token> resolveToken(Long userId) {
    return userRepository.findById(userId)
            .flatMap(tokenService::findValidToken);
}
```

왜 좋은가:

- `flatMap`으로 `Optional<Optional<Token>>`를 만들지 않는다.

## 좋은 예시 4: expensive default는 orElseGet

```java
UserProfile profile = profileRepository.findByUserId(userId)
        .orElseGet(() -> profileFactory.createDefault(userId));
```

왜 좋은가:

- 기본값 생성 비용이 있을 때 lazy supplier를 사용한다.

## 좋은 예시 5: 단건은 Optional, 다건은 빈 컬렉션

```java
public List<Role> findRoles(Long userId) {
    return roleRepository.findAllByUserId(userId);
}
```

왜 좋은가:

- 다건 결과의 부재를 `Optional<List<Role>>`로 감싸지 않는다.
- 호출자는 빈 컬렉션으로 처리하면 된다.

## 나쁜 예시 1: Optional 반환인데 null 반환

```java
public Optional<User> findByEmail(String email) {
    return null;
}
```

문제:

- `Optional` 자체가 `null`이 되어 의미가 깨진다.
- 호출자는 `Optional`과 `null`을 둘 다 처리해야 한다.

개선:

```java
public Optional<User> findByEmail(String email) {
    return Optional.empty();
}
```

## 나쁜 예시 2: 필드에 Optional 저장

```java
public class UserResponse {
    private Optional<String> nickname;
}
```

문제:

- DTO 경계에서 표현이 복잡해진다.
- 직렬화/스키마/API 계약이 불명확해질 수 있다.

개선:

```java
public class UserResponse {
    private String nickname;
}
```

또는 nullable 여부를 API 계약에서 명시한다.

## 나쁜 예시 3: 파라미터에 Optional 사용

```java
public User createUser(Optional<String> nickname) {
    // ...
}
```

문제:

- 호출자가 `Optional.empty()`와 `null` 실수를 섞기 쉽다.
- 오버로드/명시적 request object보다 의도가 약하다.

개선:

```java
public User createUser(String nickname) {
    // ...
}
```

또는:

```java
public User createUser(CreateUserCommand command) {
    // ...
}
```

## 나쁜 예시 4: isPresent + get

```java
if (userOpt.isPresent()) {
    return userOpt.get().getEmail();
}
return "unknown";
```

문제:

- imperative null-check와 다를 바 없는 패턴이다.
- `get()` 의존이 생긴다.

개선:

```java
return userOpt.map(User::getEmail)
        .orElse("unknown");
```

## 나쁜 예시 5: map 결과를 안 쓰고 side-effect

```java
userOpt.map(user -> {
    audit(user.getId());
    return user;
});
```

문제:

- `map`은 값 변환인데 반환값을 사용하지 않는다.
- side-effect 목적이면 `ifPresent`가 더 맞다.

개선:

```java
userOpt.ifPresent(user -> audit(user.getId()));
```
