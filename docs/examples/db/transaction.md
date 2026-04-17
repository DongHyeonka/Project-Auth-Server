# DB Transaction 예시

## 좋은 예시

### 예시 1. 쓰기 유스케이스 경계는 application service에서 잡는다

```java
@Service
@RequiredArgsConstructor
public class UserRoleCommandService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public void assignRole(Long userId, Long roleId) {
        User user = userRepository.getById(userId);
        Role role = roleRepository.getById(roleId);

        user.assign(role);
    }
}
```

**좋은 이유:**

- 유스케이스 전체를 하나의 트랜잭션으로 묶는다
- 여러 repository 호출을 outer service boundary가 소유한다
- controller나 repository가 아니라 application service가 일관성 경계를 대표한다

Spring Data JPA는 여러 repository 호출을 묶는 facade/service가 transactional boundary를 정의한다고 설명한다.

### 예시 2. 조회 유스케이스는 readOnly=true를 최적화 힌트로 사용한다

```java
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(Long userId) {
        User user = userRepository.getDetailById(userId)
                .orElseThrow(UserNotFoundException::new);

        return new UserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getStatus()
        );
    }
}
```

**좋은 이유:**

- 읽기 유스케이스임을 트랜잭션 속성으로 명확히 표현한다
- readOnly=true를 최적화 힌트로 사용하고, write 시도를 섞지 않는다
- 필요한 조회와 response mapping을 service 경계 안에서 끝낸다

Spring은 readOnly를 최적화 힌트로 설명하고, Spring Data JPA는 Hibernate 사용 시 flush mode를 NEVER로 두어 dirty check를 건너뛸 수 있다고 설명한다.

### 예시 3. checked exception도 롤백해야 하면 좁게 지정한다

```java
@Service
@RequiredArgsConstructor
public class UserImportService {

    private final UserRepository userRepository;

    @Transactional(rollbackFor = InvalidUserImportException.class)
    public void importUsers(List<UserImportRow> rows) throws InvalidUserImportException {
        for (UserImportRow row : rows) {
            if (!row.isValid()) {
                throw new InvalidUserImportException("invalid row");
            }
            userRepository.save(User.from(row));
        }
    }
}
```

**좋은 이유:**

- checked exception이 유스케이스 실패를 의미한다는 점을 transaction 설정에 반영한다
- rollbackFor = Exception.class처럼 과도하게 넓히지 않는다
- rollback 규칙이 예외 의미와 맞는다

Spring은 기본적으로 checked exception에서 rollback하지 않으며, rollback rules로 필요한 예외만 지정할 수 있다고 설명한다.

### 예시 4. commit 이후에만 실행돼야 하는 후속 작업은 after-commit에 연결한다

```java
@Component
public class UserCreatedEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserCreatedEvent event) {
        // 메일 발송, 후속 발행, 외부 통지 등
    }
}
```

**좋은 이유:**

- DB commit 성공 이후에만 실행돼야 하는 후속 작업을 분리한다
- 트랜잭션 내부 write와 외부 side effect를 같은 시점에 섞지 않는다
- “저장 실패인데 메일은 발송됨” 같은 불일치를 줄일 수 있다

Spring은 @TransactionalEventListener가 AFTER_COMMIT 같은 phase를 지원한다고 설명한다.

### 예시 5. outer transaction이 있어야 하는 일반 호출 체인은 REQUIRED에 맡긴다

```java
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    @Transactional
    public void confirmOrder(Long orderId) {
        Order order = orderRepository.getById(orderId);
        paymentService.validatePayment(order.getPaymentId());
        order.confirm();
    }
}

@Service
public class PaymentService {

    @Transactional
    public void validatePayment(Long paymentId) {
        // 같은 physical transaction에 참여
    }
}
```

**좋은 이유:**

- 같은 유스케이스 안에서는 기본 propagation인 REQUIRED로 충분하다
- 불필요하게 REQUIRES_NEW를 쓰지 않는다
- 하나의 물리 트랜잭션 안에서 일관성을 유지한다

Spring은 PROPAGATION_REQUIRED가 common call stack arrangement에서 좋은 기본값이라고 설명한다.

## 나쁜 예시

### 예시 1. controller에 트랜잭션을 둔다

```java
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserRoleCommandService userRoleCommandService;

    @PostMapping("/users/{id}/roles/{roleId}")
    @Transactional
    public void assignRole(@PathVariable Long id, @PathVariable Long roleId) {
        userRoleCommandService.assignRole(id, roleId);
    }
}
```

**나쁜 이유:**

- request handling 범위 전체가 DB 트랜잭션에 포함되기 쉽다
- controller는 입출력 경계이고, 일관성 경계를 소유하는 계층이 아니다
- transaction scope를 불필요하게 길게 만든다

Spring Data JPA는 facade/service가 transactional boundary를 정의한다고 설명하고, Hibernate는 물리 트랜잭션은 가능한 짧아야 한다고 설명한다.

### 예시 2. self-invocation에 @Transactional을 기대한다

```java
@Service
public class UserService {

    public void createUser(CreateUserRequest request) {
        validate(request);
        saveUser(request);
    }

    @Transactional
    void saveUser(CreateUserRequest request) {
        // 저장
    }
}
```

**나쁜 이유:**

- 같은 클래스 내부 호출이라 proxy를 통과하지 않는다
- saveUser()의 @Transactional이 실제로 적용되지 않을 수 있다
- 동작하는 것처럼 보여도 rollback 시나리오에서 깨지기 쉽다

Spring은 proxy mode에서 external method call만 interception 대상이고, self-invocation은 실제 트랜잭션을 만들지 않는다고 설명한다.

### 예시 3. readOnly=true에서 엔티티를 수정한다

```java
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public void touchLastViewedAt(Long userId) {
        User user = userRepository.getById(userId);
        user.updateLastViewedAt();
    }
}
```

**나쁜 이유:**

- readOnly=true는 쓰기 차단 장치가 아니다
- 코드 의미와 트랜잭션 의미가 서로 충돌한다
- Hibernate 최적화와 코드 의도가 어긋난다

Spring과 Spring Data JPA는 readOnly를 최적화 힌트로 설명하며, write attempt 자체를 반드시 막지 않는다고 명시한다.

### 예시 4. 외부 호출을 길게 물고 있는 long transaction

```java
@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final ExternalGatewayClient externalGatewayClient;

    @Transactional
    public void approve(Long paymentId) {
        Payment payment = paymentRepository.getById(paymentId);

        externalGatewayClient.call(payment); // 오래 걸리는 외부 호출

        payment.approve();
    }
}
```

**나쁜 이유:**

- 네트워크 대기 시간 동안 DB 트랜잭션이 열린 채로 유지될 수 있다
- lock contention과 확장성 문제가 커진다
- 외부 호출 실패와 DB 일관성 경계를 분리해서 설계해야 할 가능성이 높다

Hibernate는 DB 트랜잭션은 가능한 짧아야 하고, 긴 트랜잭션은 확장성을 해친다고 설명한다. Spring도 transaction context가 remote call로 전파되지 않는다고 설명한다.

### 예시 5. 무심코 rollbackFor = Exception.class를 붙인다

```java
@Service
public class UserService {

    @Transactional(rollbackFor = Exception.class)
    public void doSomething() throws Exception {
        // ...
    }
}
```

**나쁜 이유:**

- 모든 checked exception을 일괄 rollback 대상으로 만들어 예외 의미 구분을 흐린다
- 복구 가능한 checked exception까지 전부 트랜잭션 실패로 취급할 수 있다
- rollback 규칙이 너무 넓다

Spring은 기본 rollback 규칙이 unchecked exception 중심이며, rollback rules는 필요한 예외에 맞춰 세밀하게 설정할 수 있다고 설명한다. 따라서 광범위한 기본 확대는 신중해야 한다.

### 예시 6. 일반 helper에 REQUIRES_NEW를 붙여 부분 커밋을 만든다

```java
@Service
public class AuditHelper {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAudit(AuditLog log) {
        // 저장
    }
}
```

**나쁜 이유:**

- outer transaction과 무관하게 commit될 수 있다
- partial commit을 만들기 때문에 business semantics가 달라진다
- “트랜잭션 충돌 회피용”으로 쓰면 의도치 않은 데이터 잔존을 만든다

Spring은 REQUIRES_NEW가 항상 독립 물리 트랜잭션을 사용하고, outer rollback과 독립적으로 commit/rollback 된다고 설명한다.

### 예시 7. flush와 commit을 같은 것으로 가정한다

```java
@Transactional
public void updateUser(Long id) {
    User user = userRepository.getById(id);
    user.changeName("new-name");

    userRepository.findAll(); // "아직 commit 전이니까 DB에 영향 없겠지"라고 가정
}
```

**나쁜 이유:**

- Hibernate는 겹치는 query 실행 전 flush를 일으킬 수 있다
- commit 전에도 SQL이 먼저 나갈 수 있다
- flush timing을 잘못 이해하면 query ordering과 side effect를 오판하게 된다

Hibernate는 기본 AUTO flush 모드에서 commit 전뿐 아니라, 겹치는 JPQL/HQL query 전과 native query 전에도 flush가 일어날 수 있다고 설명한다.
