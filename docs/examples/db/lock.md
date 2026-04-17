# Lock 예시

## 좋은 예시

### 예시 1. 현재 row를 선점한 뒤 곧바로 상태를 바꾸는 작업은 NOWAIT로 fail-fast 한다

```sql
SELECT id, status
FROM billing.payments
WHERE id = :paymentId
FOR UPDATE NOWAIT;
```

**좋은 이유:**

- 같은 결제를 동시에 하나만 처리해야 할 때 즉시 충돌을 드러낸다
- 기다림보다 빠른 실패가 더 맞는 사용자 요청에 적합하다
- 가장 강한 row lock이 필요한 상황을 명확하게 표현한다

PostgreSQL은 `NOWAIT`가 락을 즉시 못 잡으면 기다리지 않고 오류를 반환한다고 설명한다. `FOR UPDATE`는 해당 row에 대한 다른 수정과 row lock을 막는다.

### 예시 2. queue claim은 SKIP LOCKED를 작업 큐에만 제한해서 사용한다

```sql
WITH picked AS (
    SELECT id
    FROM integration.outbox_events
    WHERE status = 'READY'
    ORDER BY id
    FOR UPDATE SKIP LOCKED
    LIMIT 10
)
UPDATE integration.outbox_events e
SET status = 'IN_PROGRESS',
    claimed_at = now()
FROM picked
WHERE e.id = picked.id
RETURNING e.id;
```

**좋은 이유:**

- 여러 worker가 같은 큐를 경쟁 소비할 때 이미 잠긴 row를 건너뛸 수 있다
- queue-like workload에 맞는 전형적인 사용법이다
- `ORDER BY`와 `LIMIT`를 함께 두어 claim 범위를 결정적으로 만든다

PostgreSQL은 `SKIP LOCKED`가 inconsistent view를 만들기 때문에 general-purpose work에는 부적합하지만 queue-like table에는 사용할 수 있다고 설명한다. 또한 locking clause와 `LIMIT`를 함께 쓸 수 있고, `ORDER BY` 없는 제한 조회는 예측 가능한 subset을 보장하지 않는다고 설명한다.

### 예시 3. join query에서는 OF로 실제 lock 대상만 잠근다

```sql
SELECT o.id, o.status
FROM ordering.orders o
JOIN auth.users u ON u.id = o.user_id
WHERE o.id = :orderId
FOR UPDATE OF o NOWAIT;
```

**좋은 이유:**

- 주문 row만 잠그고, join에 참여한 사용자 row까지 불필요하게 잠그지 않는다
- join이 있다고 해서 전체 테이블 범위를 넓게 잠그지 않는다
- lock 범위가 SQL만 봐도 명확하다

PostgreSQL은 locking clause에 table list를 지정하면 해당 테이블에서 나온 row만 잠그고, list를 생략하면 statement에 사용된 모든 테이블에 적용될 수 있다고 설명한다.

### 예시 4. JPA에서는 특정 단건 처리 메서드에만 PESSIMISTIC_WRITE를 붙인다

```java
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findForUpdate(@Param("id") Long id);
}
```

**좋은 이유:**

- 일반 조회 메서드와 락 조회 메서드를 분리한다
- lock intent가 repository 메서드에 명시적으로 드러난다
- 단건 처리 유스케이스에서만 비관적 락을 올린다

Spring Data JPA는 query method에 `@Lock`으로 `LockModeType`을 지정할 수 있다고 설명한다. Jakarta Persistence는 `PESSIMISTIC_WRITE`가 즉시 장기 DB 락을 얻는 pessimistic lock mode라고 설명한다.

### 예시 5. non-key 상태 수정만 예정되어 있으면 SQL 레벨에서 FOR NO KEY UPDATE를 검토한다

```sql
SELECT id, status
FROM billing.payments
WHERE id = :paymentId
FOR NO KEY UPDATE;
```

**좋은 이유:**

- 이후 status 같은 non-key 컬럼만 바꿀 예정이라면 `FOR UPDATE`보다 약한 락으로 충분할 수 있다
- 필요 이상으로 강한 락을 쓰지 않는다
- ordinary update semantics와 더 잘 맞는다

PostgreSQL은 `FOR NO KEY UPDATE`가 `FOR UPDATE`보다 약하고, key를 바꾸지 않는 일반 UPDATE가 이 수준의 락을 획득한다고 설명한다.

## 나쁜 예시

### 예시 1. 일반 목록 조회에 PESSIMISTIC_WRITE를 건다

```java
public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Page<User> findAll(Pageable pageable);
}
```

**나쁜 이유:**

- 단건 선점이 아니라 넓은 목록 조회에 비관적 락을 건다
- 대기, contention, deadlock 가능성을 크게 올린다
- locking use case와 plain listing use case가 섞여 있다

Spring Data JPA는 CRUD/query method에 `@Lock`을 적용할 수 있지만, PostgreSQL row lock은 같은 row에 대한 writer/locker를 막고 대기를 만들 수 있다. 이런 넓은 조회에 쓰면 비용이 과도하다.

### 예시 2. 일반 사용자 목록 API에 SKIP LOCKED를 사용한다

```sql
SELECT id, email
FROM auth.users
WHERE deleted_at IS NULL
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 20;
```

**나쁜 이유:**

- 일반 업무 조회에서 잠긴 row를 조용히 건너뛰면 결과 집합 의미가 깨진다
- 운영자와 사용자 모두 "왜 어떤 데이터가 안 보였는지" 설명하기 어렵다
- `SKIP LOCKED`는 queue-like workload가 아닌 경우 기본적으로 부적절하다

PostgreSQL은 `SKIP LOCKED`가 inconsistent view를 만들기 때문에 general-purpose work에는 적합하지 않다고 직접 경고한다.

### 예시 3. queue claim query에 OFFSET을 넣는다

```sql
SELECT id
FROM integration.outbox_events
WHERE status = 'READY'
ORDER BY id
OFFSET 100
LIMIT 10
FOR UPDATE SKIP LOCKED;
```

**나쁜 이유:**

- `OFFSET`으로 건너뛴 row도 잠길 수 있다
- claim 범위와 실제 lock 범위가 어긋날 수 있다
- queue 소비 문맥에서는 특히 예측이 어려워진다

PostgreSQL은 locking clause와 함께 `LIMIT`를 쓸 때 필요한 row까지만 잠그지만, `OFFSET`으로 건너뛴 row도 잠길 수 있다고 설명한다.

### 예시 4. ordinary row 처리에 LOCK TABLE을 사용한다

```sql
LOCK TABLE billing.payments IN ACCESS EXCLUSIVE MODE;
```

**나쁜 이유:**

- plain SELECT까지 막을 수 있는 매우 강한 table-level lock이다
- 특정 row 경쟁을 해결하려는 문제에 비해 범위가 과도하다
- 일반 비즈니스 처리에 쓰기에는 영향 범위가 너무 크다

PostgreSQL은 `ACCESS EXCLUSIVE`가 모든 lock mode와 충돌하고, plain SELECT를 막는 유일한 lock mode라고 설명한다.

### 예시 5. 락을 잡은 뒤 외부 호출을 오래 수행한다

```java
@Transactional
public void approve(Long paymentId) {
    Payment payment = paymentRepository.findForUpdate(paymentId)
            .orElseThrow();

    externalGatewayClient.call(payment); // 오래 걸리는 네트워크 호출

    payment.approve();
}
```

**나쁜 이유:**

- row lock을 쥔 채 외부 대기 시간을 모두 끌고 간다
- 다른 트랜잭션이 같은 row를 오래 기다리게 만든다
- deadlock과 lock wait 문제를 악화시킨다

PostgreSQL은 lock 요청이 deadlock이 아니면 오래 기다릴 수 있고, 애플리케이션이 긴 시간 트랜잭션을 열어 두는 것은 나쁜 생각이라고 설명한다. idle in transaction도 오래 유지되면 문제가 된다고 설명한다.

### 예시 6. join query에서 lock 대상을 좁히지 않는다

```sql
SELECT o.id, u.id
FROM ordering.orders o
JOIN auth.users u ON u.id = o.user_id
WHERE o.id = :orderId
FOR UPDATE;
```

**나쁜 이유:**

- 실제로는 order row만 선점하면 되는데 join 참여 row까지 넓게 잠글 수 있다
- lock 범위가 과도하고, 예상보다 큰 contention을 만들 수 있다
- 이런 query는 `FOR UPDATE OF o` 같은 형태로 범위를 좁히는 편이 안전하다

PostgreSQL은 locking clause에서 table list를 생략하면 statement에 사용된 모든 테이블에 영향을 줄 수 있다고 설명한다.

### 예시 7. PessimisticLockException과 LockTimeoutException을 같은 것으로 처리한다

```java
try {
    repository.findForUpdate(id);
} catch (PersistenceException e) {
    return;
}
```

**나쁜 이유:**

- transaction-level rollback이 난 경우와 statement-level timeout만 난 경우를 구분하지 못한다
- 후속 처리와 재시도 정책이 흐려진다
- 잠금 실패 의미를 application boundary에서 잃어버린다

Jakarta Persistence는 pessimistic lock failure가 transaction rollback이면 `PessimisticLockException`, statement rollback이면 `LockTimeoutException`이라고 구분한다. 두 예외는 현재 트랜잭션 상태도 다를 수 있다.
