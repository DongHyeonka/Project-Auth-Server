# duplication 예시

## 좋은 예시 1: 같은 정책 중복은 private method로 추출

```java
private String normalizeEmail(String rawEmail) {
    return rawEmail.trim().toLowerCase(Locale.ROOT);
}

public User register(String rawEmail) {
    String email = normalizeEmail(rawEmail);
    ...
}

public User login(String rawEmail) {
    String email = normalizeEmail(rawEmail);
    ...
}
```

왜 좋은가:

- 같은 정책이다
- 같은 이유로 바뀐다
- 한 곳에서 수정 가능하다

## 좋은 예시 2: 외부 API 예외 번역 중복 추출

```java
private InfrastructureException vaultFailure(String message, Exception cause) {
    return new InfrastructureException(
            InfrastructureErrorCode.VAULT_TRANSIT_FAILED,
            message,
            cause
    );
}
```

왜 좋은가:

- 기술 실패 번역 정책이 한 곳에 모인다
- 누락/불일치 위험이 줄어든다

## 좋은 예시 3: 테스트는 중복을 일부 허용

```java
@Test
void registers_two_users() {
    User user1 = new User("alice");
    User user2 = new User("bob");

    forum.register(user1);
    forum.register(user2);

    assertTrue(forum.hasRegisteredUser(user1));
    assertTrue(forum.hasRegisteredUser(user2));
}
```

왜 좋은가:

- helper/loop보다 읽기 쉽다
- 테스트 의도가 바로 드러난다

## 좋은 예시 4: 3회 이상 반복되는 mapper 규칙 추출

```java
private ApiResult<Void> failureOf(ApplicationException exception) {
    return ApiResult.failure(exception.getCode(), exception.getMessage());
}
```

왜 좋은가:

- 응답 실패 조립 규칙이 공통 정책이다
- presentation 전반에서 같은 이유로 바뀔 가능성이 높다

## 나쁜 예시 1: 우연한 유사성을 억지로 공통화

```java
public Object process(Object input, String mode, Map<String, Object> options) {
    ...
}
```

문제:

- 맥락이 다른 두세 개 흐름을 한 메서드로 억지로 합친다
- 이름이 모호해지고 분기만 늘어난다
- 이후 독립 진화가 어렵다

## 나쁜 예시 2: 레이어를 넘는 공통화

```java
public final class CommonValidationUtil {
    public static void validateUser(User user, CreateUserRequest request, UserJpaEntity entity) {
        ...
    }
}
```

문제:

- domain/presentation/infrastructure 경계를 한 곳에 섞는다
- 중복 제거보다 아키텍처 손상이 더 크다

## 나쁜 예시 3: 테스트를 너무 DRY하게 만들어 의미 숨김

```java
private void registerAll(List<User> users) { ... }

@Test
void registers_users() {
    registerAll(defaultUsers());
    assertAllRegistered(defaultUsers());
}
```

문제:

- 테스트 본문만 보면 실제 행위가 잘 드러나지 않는다
- helper를 따라가야 해서 검증이 어려워진다

## 나쁜 예시 4: common 모듈로 너무 빨리 이동

```text
common/
  StringUtils.java
  DateUtils.java
  ErrorUtils.java
  ValidationUtils.java
```

문제:

- “중복 제거” 명분으로 소유권 없는 잡동사니 모듈이 된다
- 진짜 공통인지, 그냥 아직 설계가 안 된 것인지 구분이 사라진다
