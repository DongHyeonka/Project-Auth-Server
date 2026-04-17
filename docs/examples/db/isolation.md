# Transaction Isolation 예시

## 좋은 예시

### 예시 1. 일반적인 서비스 로직은 기본값을 따른다

```java
@Service
@RequiredArgsConstructor
public class UserCommandService {

    private final UserRepository userRepository;

    @Transactional
    public void changeDisplayName(Long userId, String newName) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        user.changeDisplayName(newName);
    }
}
```

**좋은 이유:**

- Spring 기본값인 `Isolation.DEFAULT`를 사용한다
- PostgreSQL에서는 보통 `READ COMMITTED`가 적용된다
- 일반적인 단건 수정 유스케이스에 과도한 isolation을 강제하지 않는다

Spring은 `@Transactional` 기본 isolation이 `ISOLATION_DEFAULT`라고 설명하고, PostgreSQL은 기본 isolation이 보통 `READ COMMITTED`라고 설명한다.

### 예시 2. 같은 트랜잭션 안에서 stable snapshot이 필요하면 REPEATABLE_READ를 검토한다

```java
@Service
@RequiredArgsConstructor
public class SettlementPreviewService {

    private final SettlementRepository settlementRepository;

    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public SettlementPreview preview(Long merchantId, LocalDate from, LocalDate to) {
        List<SettlementLine> lines = settlementRepository.findLines(merchantId, from, to);
        BigDecimal fee = settlementRepository.sumFee(merchantId, from, to);

        return SettlementPreview.of(lines, fee);
    }
}
```

**좋은 이유:**

- 여러 query가 같은 snapshot을 기준으로 계산되길 원할 때 의미가 있다
- PostgreSQL의 `REPEATABLE READ`는 트랜잭션 시작 시점 snapshot을 유지한다
- 단, 이 설계는 여전히 serialization failure 재시도 필요성을 함께 고려해야 한다

PostgreSQL은 `REPEATABLE READ`에서 successive SELECT가 같은 snapshot을 보고, serialization failure에 대비해야 한다고 설명한다.

### 예시 3. cross-row invariant가 중요하면 SERIALIZABLE과 재시도를 함께 둔다

```java
@Service
@RequiredArgsConstructor
public class SeatAllocationService {

    private final SeatRepository seatRepository;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void allocateSeat(Long eventId, Long userId) {
        if (seatRepository.countAllocated(eventId) >= seatRepository.capacityOf(eventId)) {
            throw new NoSeatLeftException();
        }

        seatRepository.insertAllocation(eventId, userId);
    }
}
```

**좋은 이유:**

- 집합 단위 정합성이 중요한 유스케이스를 명시적으로 serial semantics로 올린다
- 단순 snapshot 안정성이 아니라 serialization anomaly 방지가 목적이다
- 이 경우 40001 전체 재시도 정책이 같이 있어야 설계가 완성된다

PostgreSQL은 `SERIALIZABLE`이 serial execution과 같은 효과를 보장하지만, serialization failure가 발생할 수 있으므로 재시도가 필요하다고 설명한다.

### 예시 4. stronger isolation보다 더 직접적인 수단이 있으면 그쪽을 먼저 쓴다

```sql
UPDATE billing.payments
SET status = 'CONFIRMED',
    confirmed_at = now()
WHERE id = :paymentId
  AND status = 'PENDING';
```

**좋은 이유:**

- 단순 상태 전이는 stronger isolation보다 조건부 UPDATE가 더 직접적이다
- `READ COMMITTED`에서도 원자적으로 성공 여부를 판단할 수 있다
- isolation level을 과도하게 올리지 않아도 된다

PostgreSQL은 `READ COMMITTED`에서 concurrent update 시 WHERE 조건이 재평가될 수 있고, 조건부 mutation이 유용하게 동작한다고 설명한다.

### 예시 5. inner method isolation override를 기대하지 않고 outer boundary에서 선언한다

```java
@Service
@RequiredArgsConstructor
public class ReportFacade {

    private final ReportQueryService reportQueryService;

    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public ReportResponse generate(Long reportId) {
        return reportQueryService.generate(reportId);
    }
}
```

**좋은 이유:**

- isolation을 outer use case boundary에서 선언한다
- inner service가 기존 트랜잭션에 참여하면서 의미가 흐려지는 것을 피한다
- Spring의 isolation 적용 규칙과 맞다

Spring은 isolation setting이 새로 시작된 트랜잭션에만 적용되고, 기존 트랜잭션에 참여하는 inner scope의 local isolation은 기본적으로 무시된다고 설명한다.

## 나쁜 예시

### 예시 1. PostgreSQL에서 READ_UNCOMMITTED를 dirty read 용도로 기대한다

```java
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public User findUser(Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**나쁜 이유:**

- PostgreSQL에서는 `READ UNCOMMITTED`가 별도 dirty-read 모드로 동작하지 않는다
- 실제로는 `READ COMMITTED`처럼 동작한다
- 성능이나 동작 의미가 달라질 것이라 기대하면 틀린 설계가 된다

PostgreSQL은 `READ UNCOMMITTED`가 내부적으로 `READ COMMITTED`처럼 동작한다고 명시한다.

### 예시 2. READ_COMMITTED에서 같은 트랜잭션 안의 두 조회가 반드시 같을 것이라 가정한다

```java
@Transactional
public boolean canStillShip(Long orderId) {
    Order order1 = orderRepository.findById(orderId).orElseThrow();

    // 중간에 다른 트랜잭션이 상태를 바꿀 수 있음

    Order order2 = orderRepository.findById(orderId).orElseThrow();
    return order1.getStatus() == order2.getStatus();
}
```

**나쁜 이유:**

- PostgreSQL `READ COMMITTED`에서는 각 query가 자기 시작 시점 snapshot을 본다
- 따라서 같은 트랜잭션 안에서도 두 조회 결과가 달라질 수 있다
- stable snapshot이 필요한 로직이라면 isolation 또는 더 직접적인 동시성 제어를 다시 설계해야 한다

PostgreSQL은 `READ COMMITTED`에서 subsequent commands in the same transaction이 committed concurrent transaction의 효과를 보게 된다고 설명한다.

### 예시 3. REPEATABLE_READ를 쓰면서 serialization failure 재시도를 준비하지 않는다

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void runComplexSettlement(Long merchantId) {
    // 복잡한 다단계 조회/갱신
}
```

**나쁜 이유:**

- PostgreSQL의 `REPEATABLE READ`도 serialization anomaly를 막기 위해 실패할 수 있다
- stronger isolation만 올리고 재시도 정책을 설계하지 않으면 운영 시 장애로 이어질 수 있다
- "repeatable read니까 실패는 없을 것"이라는 가정이 틀리다

PostgreSQL은 `REPEATABLE READ`에서도 애플리케이션이 serialization failure 재시도를 준비해야 한다고 설명한다.

### 예시 4. SERIALIZABLE을 전역 기본값처럼 남발한다

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void doAnything() {
    // 일반 CRUD, 단순 조회, 목록 조회까지 전부 같은 정책
}
```

**나쁜 이유:**

- serial semantics가 필요하지 않은 경로에도 monitoring overhead와 retry 부담을 강제한다
- 더 직접적인 수단으로 닫을 수 있는 문제까지 모두 isolation으로 해결하려 든다
- stronger isolation은 증명 가능한 요구가 있을 때만 써야 한다

PostgreSQL은 `SERIALIZABLE`이 monitoring overhead를 가지며, serialization failure를 일으킬 수 있다고 설명한다.

### 예시 5. inner method에서 isolation을 바꾸면 outer transaction을 override할 수 있다고 기대한다

```java
@Service
public class OuterService {

    @Transactional
    public void outer() {
        inner();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void inner() {
        // 여기서 isolation이 바뀔 것이라고 기대
    }
}
```

**나쁜 이유:**

- 기존 트랜잭션에 참여하면 inner isolation 선언은 기본적으로 무시될 수 있다
- 게다가 self-invocation 구조라 transaction advice 자체가 적용되지 않을 수도 있다
- isolation override 의도가 코드에 반영되지 않는다

Spring은 isolation setting이 새 트랜잭션에만 적용되고, 기존 트랜잭션 참여 시 local isolation은 기본적으로 무시된다고 설명한다.

### 예시 6. sequence 값이 rollback되리라 기대한다

```sql
BEGIN;
INSERT INTO auth.users DEFAULT VALUES;
ROLLBACK;
```

**나쁜 이유:**

- sequence/serial 계열 값은 다른 트랜잭션에 즉시 visible할 수 있고 rollback되지 않는다
- gap 없는 연속 번호를 기대하면 안 된다
- 이는 isolation 문제가 아니라 PostgreSQL sequence 동작 특성이다

PostgreSQL은 sequence 변경이 즉시 visible하고 transaction abort 시에도 rollback되지 않는다고 설명한다.
