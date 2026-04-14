# interface 생성 예시

## 좋은 예시 1: application output port

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

- application이 persistence 구현을 모른다
- 바깥 구현 교체와 테스트 대역 주입이 쉽다
- 레이어 경계가 분명하다

## 좋은 예시 2: 외부 시스템 client contract

```java
public interface VaultTransitClient {
    String sign(String keyName, byte[] input);
    PublicKey readPublicKey(String keyName);
}
```

**왜 좋은가:**

- 외부 연동 경계가 분명하다
- HttpClient/WebClient/Jackson 세부가 계약에 새지 않는다
- fake/stub 구현으로 테스트하기 쉽다

## 좋은 예시 3: 교체 가능한 정책 객체

```java
public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
```

**왜 좋은가:**

- 알고리즘 교체 가능성이 실제로 있다
- application/domain이 구체 해시 구현을 모른다

## 좋은 예시 4: 인터페이스 없이 concrete class 유지

```java
@Component
public class LoginResponseAssembler {
    public LoginResponse toResponse(User user, TokenPair tokenPair) {
        ...
    }
}
```

**왜 좋은가:**

- 내부 presentation helper일 뿐 계약 경계가 아니다
- 구현체 1개, 교체 가치 낮음, 인터페이스 이득 작음
- 불필요한 LoginResponseAssemblerImpl을 만들지 않는다

## 나쁜 예시 1: 의미 없는 Service/Impl 쌍

```java
public interface UserService {
    User create(CreateUserCommand command);
}

@Service
public class UserServiceImpl implements UserService {
    ...
}
```

**문제:**

- 실제 경계/교체/테스트 seam 의미가 약하다
- 타입만 늘고 추상화 이득이 거의 없다
- “관성적인 인터페이스”에 가깝다

## 나쁜 예시 2: 기술 세부를 계약에 노출

```java
public interface UserClient {
    ResponseEntity<String> getUser(String id);
}
```

**문제:**

- Spring Web 타입이 계약에 박힌다
- 호출자가 구현 기술에 묶인다

**개선:**

- 도메인/애플리케이션에 더 맞는 결과 타입으로 계약 정의

## 나쁜 예시 3: 여러 책임을 한 인터페이스에 몰아넣기

```java
public interface UserManager {
    User findUser(...);
    User saveUser(...);
    void sendEmail(...);
    String issueToken(...);
}
```

**문제:**

- 하나의 역할이 아니다
- 호출자마다 일부만 필요할 가능성이 높다
- 응집도가 낮다

## 나쁜 예시 4: 조기 추상화

```java
public interface DeadlineService {
    void setDeadline(...);
}

public class TaskDeadlineService implements DeadlineService { ... }

public class PaymentDeadlineService implements DeadlineService { ... }
```

**문제:**

- 지금은 비슷해 보여도 미래에 다르게 진화할 수 있다
- 아직 공통 계약이 자연스러운지 검증되지 않았다

**개선 방향:**

- 충분한 공통성/경계 필요가 생길 때까지 분리된 concrete class 유지
