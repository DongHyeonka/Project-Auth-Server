# ApplicationEvent 예시

## 좋은 예시 1: 핵심 작업 후 후속 반응 분리

```java
public record UserRegisteredEvent(
        Long userId,
        String email,
        Instant occurredAt
) {}

@Service
public class RegisterUserService {

    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegisterUserService(ApplicationEventPublisher eventPublisher, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Long register(CreateUserCommand command) {
        User user = ...;
        userRepository.save(user);

        eventPublisher.publishEvent(
                new UserRegisteredEvent(user.getId(), user.getEmail(), Instant.now(clock))
        );

        return user.getId();
    }
}
```

**왜 좋은가:**

- 핵심 등록 작업과 후속 반응을 분리한다
- 이벤트 payload가 필요한 상태를 직접 담는다
- publisher가 listener 구현을 모른다

## 좋은 예시 2: commit 후에만 처리

```java
@Component
public class UserRegisteredAuditListener {

    @TransactionalEventListener
    public void handle(UserRegisteredEvent event) {
        auditLog.record("USER_REGISTERED", event.userId(), event.occurredAt());
    }
}
```

**왜 좋은가:**

- 기본 AFTER_COMMIT 의미를 활용한다
- rollback된 작업에 대해 잘못된 후속 기록을 남기지 않는다

## 좋은 예시 3: listener는 짧고 부가적

```java
@Component
public class WelcomeMetricListener {

    @EventListener
    public void handle(UserRegisteredEvent event) {
        metrics.counter("user.registered").increment();
    }
}
```

**왜 좋은가:**

- 짧고 효율적이다
- 핵심 비즈니스 흐름을 숨기지 않는다

## 좋은 예시 4: 테스트에서 이벤트 검증

```java
@RecordApplicationEvents
@SpringBootTest
class RegisterUserServiceTest {

    @Test
    void publishes_user_registered_event(ApplicationEvents events) {
        service.register(command);

        assertThat(events.stream(UserRegisteredEvent.class)).hasSize(1);
    }
}
```

**왜 좋은가:**

- 이벤트 발행 사실을 테스트로 확인할 수 있다
- “어딘가에서 되겠지” 상태를 줄인다

## 나쁜 예시 1: 핵심 오케스트레이션을 이벤트에 숨김

```java
@Service
public class LoginService {

    public LoginResponse login(LoginCommand command) {
        eventPublisher.publishEvent(new LoginRequestedEvent(command));
        return LoginResponse.pending();
    }
}
```

**문제:**

- 핵심 로그인 흐름이 listener들 뒤로 숨어버린다
- 메인 결과가 이벤트 체인에 의존하게 된다

## 나쁜 예시 2: payload가 너무 빈약함

```java
public record UserRegisteredEvent(Long userId) {}
```

**문제:**

- 모든 listener가 다시 조회를 강요받을 수 있다
- 필요한 최소 상태가 누락되면 결합과 조회 비용이 커진다

**개선:**

- 정말 필요한 상태를 payload에 포함

## 나쁜 예시 3: 무거운 작업을 listener에 직접 넣음

```java
@Component
public class HeavyListener {

    @EventListener
    public void handle(UserRegisteredEvent event) {
        externalApi.call(...);
        fileExporter.export(...);
        Thread.sleep(5000);
    }
}
```

**문제:**

- listener가 너무 무겁고 느리다
- event hand-off의 장점을 해친다
- 장애 반경이 커진다

## 나쁜 예시 4: listener 순서에 핵심 의존

```java
@Component
class FirstListener {
    @Order(1)
    @EventListener
    void handle(UserRegisteredEvent event) { ... }
}

@Component
class SecondListener {
    @Order(2)
    @EventListener
    void handle(UserRegisteredEvent event) { ... } // 첫 번째가 반드시 먼저 돌 것을 기대
}
```

**문제:**

- 이벤트 기반 구조가 사실상 숨은 절차형 흐름이 된다
- 순서 의존이 커질수록 명시적 호출이 더 낫다
