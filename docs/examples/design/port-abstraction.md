# port abstraction 예시

## 좋은 예시 1: outbound port를 application이 소유

```java
public interface UserReader {
    Optional<User> findByEmail(UserEmail email);
    Optional<User> findById(UserId userId);
}

@Repository
public class JpaUserReader implements UserReader {
    ...
}
```

**왜 좋은가:**

- application이 persistence 기술을 모른다
- 코어가 필요한 조회 능력만 계약으로 드러난다
- adapter만 JPA를 안다

## 좋은 예시 2: inbound port를 use case 계약으로 사용

```java
public interface LoginUseCase {
    LoginResult login(LoginCommand command);
}

@RestController
class LoginController {
    private final LoginUseCase loginUseCase;
    ...
}
```

**왜 좋은가:**

- controller가 구현체보다 use case 계약에 의존한다
- HTTP 세부와 비즈니스 흐름이 분리된다

## 좋은 예시 3: external API 경계 포트

```java
public interface TokenSigner {
    Signature sign(SigningRequest request);
}

public class VaultTokenSigner implements TokenSigner {
    ...
}
```

**왜 좋은가:**

- 포트는 “서명한다”는 능력만 표현한다
- HTTP, JSON, Vault path/header는 adapter 구현으로 숨긴다

## 좋은 예시 4: 하나의 포트에 여러 adapter 가능

```java
public interface RateRepository {
    BigDecimal findDiscountRate(Money amount);
}

public class InMemoryRateRepository implements RateRepository { ... }

public class JdbcRateRepository implements RateRepository { ... }
```

**왜 좋은가:**

- 테스트와 운영 구현이 같은 계약을 공유한다
- 포트는 기술 수와 무관하게 같은 대화를 표현한다

## 나쁜 예시 1: 기술 타입이 새는 포트

```java
public interface UserApiPort {
    ResponseEntity<String> getUser(String id);
}
```

**문제:**

- HTTP 세부가 코어 계약으로 올라온다
- 비즈니스 의미가 아니라 transport 형식이 중심이 된다

## 나쁜 예시 2: adapter 편의 중심 포트

```java
public interface DatabasePort {
    String query(String sql);
}
```

**문제:**

- 코어가 SQL/DB 기술 세부를 알게 된다
- “무엇을 원하나”가 아니라 “어떻게 하냐”를 말한다

## 나쁜 예시 3: 너무 범용적인 outbound port

```java
public interface ExternalSystemPort {
    Object execute(Object input);
}
```

**문제:**

- 역할이 불명확하다
- 타입 안정성과 계약 의미가 없다
- 여러 외부 시스템 책임을 한 곳에 섞기 쉽다

## 나쁜 예시 4: 내부 helper까지 포트화

```java
public interface EmailNormalizerPort {
    String normalize(String raw);
}
```

**문제:**

- 외부 경계가 아니라 내부 로직 detail이다
- 포트 추상화 비용이 이득보다 크다
