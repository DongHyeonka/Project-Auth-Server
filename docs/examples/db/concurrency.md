# DB Concurrency 예시

## 좋은 예시

### 예시 1. 동시에 수정될 수 있는 aggregate root에는 @Version을 둔다

```java
@Entity
@Table(name = "users", schema = "auth")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
```

**좋은 이유:**

- 같은 사용자를 여러 요청이 동시에 수정할 때 conflicting update를 감지할 수 있다
- JPA 표준 메커니즘이라 provider portability가 높다
- 숫자형 version은 timestamp보다 기본값으로 더 신뢰하기 쉽다

Jakarta Persistence는 version 필드가 있는 엔티티에 대해 provider가 자동 optimistic locking을 수행해야 한다고 설명하고, Hibernate는 `@Version`이 lost update를 막는 기본 메커니즘이라고 설명합니다.

### 예시 2. 상태 전이는 조건부 UPDATE 한 번으로 처리한다

```sql
UPDATE billing.payments
SET status = 'CONFIRMED',
    confirmed_at = now()
WHERE id = :paymentId
  AND status = 'PENDING'
RETURNING id, status, confirmed_at;
```

**좋은 이유:**

- PENDING일 때만 확정된다
- 선조회와 후행 update를 분리하지 않아 경쟁 상태를 줄인다
- 반환 row가 없으면 이미 다른 트랜잭션이 상태를 바꿨다고 해석할 수 있다

PostgreSQL은 concurrent update 뒤 WHERE 조건을 다시 평가하고, `UPDATE ... RETURNING`으로 실제 반영된 row를 바로 받을 수 있다고 설명합니다.

### 예시 3. 중복 생성은 UNIQUE + ON CONFLICT로 닫는다

```sql
CREATE TABLE integration.webhook_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider text NOT NULL,
    provider_event_id text NOT NULL,
    payload jsonb NOT NULL,
    CONSTRAINT uq_webhook_events__provider_event_id
        UNIQUE (provider, provider_event_id)
);

INSERT INTO integration.webhook_events (provider, provider_event_id, payload)
VALUES (:provider, :eventId, CAST(:payload AS jsonb))
ON CONFLICT (provider, provider_event_id) DO NOTHING
RETURNING id;
```

**좋은 이유:**

- 중복 webhook 반영을 애플리케이션 선조회가 아니라 DB 제약으로 닫는다
- high concurrency에서도 중복 row가 생기지 않는다
- 성공 여부를 RETURNING 결과 존재로 판정할 수 있다

PostgreSQL은 제약 위반 시 저장을 막고, `ON CONFLICT`가 high concurrency에서도 atomic outcome을 제공한다고 설명합니다.

### 예시 4. create-or-update는 UPSERT로 처리한다

```sql
INSERT INTO auth.login_failures (user_id, fail_count, last_failed_at)
VALUES (:userId, 1, now())
ON CONFLICT (user_id)
DO UPDATE
SET fail_count = auth.login_failures.fail_count + 1,
    last_failed_at = EXCLUDED.last_failed_at
RETURNING user_id, fail_count, last_failed_at;
```

**좋은 이유:**

- "없으면 생성, 있으면 누적 갱신"을 한 문장으로 처리한다
- 경쟁 상태에서 insert/update 사이가 찢어지지 않는다
- 원자적 upsert semantics를 그대로 활용한다

PostgreSQL은 `ON CONFLICT DO UPDATE`가 atomic한 insert-or-update outcome을 보장한다고 설명합니다.

### 예시 5. 낙관적 락 실패는 유스케이스 실패로 올린다

```java
@Transactional
public void changeDisplayName(Long userId, String newName) {
    User user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);

    user.changeDisplayName(newName);

    entityManager.flush();
}
```

**좋은 이유:**

- optimistic lock 충돌을 트랜잭션 후반 commit 시점이 아니라, 서비스 내부에서 더 빨리 드러나게 할 수 있다
- 실패를 조기에 감지하고 응답 정책을 결정하기 쉽다
- 예외를 숨기지 않고 유스케이스 실패로 처리한다

Jakarta Persistence는 optimistic lock check가 commit 시점까지 지연될 수 있고, 더 일찍 처리해야 하면 `flush()`를 사용할 수 있다고 설명합니다.

## 나쁜 예시

### 예시 1. version 없이 마지막 커밋이 이기게 둔다

```java
@Entity
@Table(name = "users", schema = "auth")
public class User {

    @Id
    private Long id;

    @Column(name = "display_name", nullable = false)
    private String displayName;
}
```

**나쁜 이유:**

- 두 요청이 같은 사용자를 수정하면 나중에 commit한 요청이 앞선 변경을 덮어쓸 수 있다
- detached merge나 동시 수정에서 lost update 위험이 크다
- 표준 optimistic locking 보호가 없다

Jakarta Persistence는 version이 없는 엔티티는 애플리케이션이 직접 일관성을 책임져야 하고, version을 쓰지 않으면 lost update와 inconsistent state를 초래할 수 있다고 설명합니다.

### 예시 2. 먼저 읽고, 자바에서 검사한 뒤, 별도 UPDATE를 날린다

```java
@Transactional
public void confirmPayment(Long paymentId) {
    Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(PaymentNotFoundException::new);

    if (payment.getStatus() != PaymentStatus.PENDING) {
        throw new IllegalStateException("not pending");
    }

    payment.confirm();
}
```

**나쁜 이유:**

- 같은 PENDING row를 두 트랜잭션이 동시에 읽고 둘 다 confirm하려고 할 수 있다
- 버전 락이나 조건부 update가 없으면 경쟁 상태를 막기 어렵다
- 상태 전이 조건이 DB 최종 판정이 아니라 애플리케이션 선조회에만 의존한다

PostgreSQL은 concurrent update 시 row를 다시 적용하고 WHERE를 재평가한다고 설명하므로, 이런 read-then-act 패턴보다 단일 조건부 mutation이 더 안전합니다.

### 예시 3. 중복 생성 방지를 선조회로만 처리한다

```java
@Transactional
public void registerWebhookEvent(String provider, String eventId, String payload) {
    boolean exists = webhookEventRepository.existsByProviderAndProviderEventId(provider, eventId);
    if (exists) {
        return;
    }

    webhookEventRepository.save(new WebhookEvent(provider, eventId, payload));
}
```

**나쁜 이유:**

- 두 트랜잭션이 동시에 exists = false를 볼 수 있다
- UNIQUE 제약이 없으면 중복 row가 실제로 저장될 수 있다
- 선조회는 보조일 수 있어도, 최종 정합성 보장 수단이 아니다

PostgreSQL은 제약이 저장 시점의 위반을 막는다고 설명합니다. 이런 문제는 UNIQUE 없이 안전하지 않습니다.

### 예시 4. OptimisticLockException을 잡아서 무시한다

```java
@Transactional
public void changeDisplayName(Long userId, String newName) {
    try {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.changeDisplayName(newName);
        entityManager.flush();
    } catch (OptimisticLockException ignored) {
        // 무시
    }
}
```

**나쁜 이유:**

- 현재 트랜잭션은 이미 rollback 대상으로 표시될 수 있다
- 유스케이스 실패를 숨기고 잘못된 성공처럼 보이게 만든다
- 재시도 여부는 상위 application boundary에서 다시 판단해야 한다

Jakarta Persistence는 optimistic lock failure 시 `OptimisticLockException`을 던지고 현재 트랜잭션을 rollback 대상으로 표시해야 한다고 규정합니다.

### 예시 5. 핵심 필드를 optimistic lock에서 제외한다

```java
@Entity
public class Payment {

    @Id
    private Long id;

    @OptimisticLock(excluded = true)
    private BigDecimal amount;

    @Version
    private Long version;
}
```

**나쁜 이유:**

- 금액처럼 핵심 비즈니스 필드를 제외하면 concurrent overwrite를 받아들인다는 뜻이 된다
- 이런 필드는 lost update를 허용하면 안 된다
- `excluded=true`는 부수적 카운터 같은 제한된 경우에만 의미가 있다

Hibernate는 `excluded` 속성이 version 증가를 막아 lost update를 수용할 수 있는 필드에만 써야 함을 예시로 설명합니다.

### 예시 6. unique violation을 무한 재시도한다

```java
while (true) {
    try {
        userRepository.save(user);
        break;
    } catch (DataIntegrityViolationException e) {
        // 계속 재시도
    }
}
```

**나쁜 이유:**

- unique violation은 transient concurrency일 수도 있지만, 영구적인 비즈니스 오류일 수도 있다
- 원인을 구분하지 않으면 무한 루프나 불필요한 부하를 만든다
- 재시도는 전체 유스케이스와 에러 코드 의미를 보고 제한적으로만 해야 한다

PostgreSQL은 23505가 때로는 concurrency 실패일 수 있지만, 항상 그런 것은 아니므로 더 신중해야 한다고 설명합니다.

### 예시 7. 단순 중복 생성 문제를 곧바로 FOR UPDATE로 푼다

```sql
SELECT *
FROM auth.users
WHERE email = :email
FOR UPDATE;
```

**나쁜 이유:**

- 중복 생성 방지의 기본 문제는 uniqueness인데, row lock을 먼저 가져가면 설계가 과도하게 무거워진다
- 아직 row가 없는 경우에는 lock으로도 해결되지 않는다
- 이런 문제는 보통 unique constraint와 `ON CONFLICT`가 더 직접적이다

PostgreSQL은 `FOR UPDATE`가 기존 row를 잠그는 수단이라고 설명합니다. "존재하지 않는 row의 uniqueness"는 제약이 더 적절합니다.
