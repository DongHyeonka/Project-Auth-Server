# JPA Fetch Strategy 예시

## 좋은 예시

### 예시 1. to-one 기본값도 명시적으로 LAZY로 바꾼다

```java
@Entity
@Table(name = "sessions", schema = "auth")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}
```

**좋은 이유:**

- JPA 기본값인 @ManyToOne EAGER에 기대지 않는다
- 세션 조회에서 항상 사용자 전체를 강제로 가져오지 않는다
- 필요한 경우 query 또는 entity graph에서만 user를 함께 가져오게 만들 수 있다

Jakarta Persistence는 @ManyToOne 기본 fetch가 EAGER라고 정의하고, Hibernate는 to-one EAGER 기본값을 그대로 쓰기보다 모든 association을 LAZY로 두는 편을 권장한다.

### 예시 2. 상세 조회의 to-one 연관은 join fetch로 가져온다

```java
@Query("""
    select s
    from Session s
    join fetch s.user u
    where s.id = :id
""")
Optional<Session> findDetailById(@Param("id") Long id);
```

**좋은 이유:**

- 기본 매핑은 LAZY로 두고, 이 상세 조회에서만 user를 함께 가져온다
- to-one 연관 fetch join은 Hibernate가 권장하는 대표 패턴이다
- use case 단위로 eager 요구를 제어한다

Hibernate는 join fetch가 laziness를 override하여 같은 SQL join으로 데이터를 가져오게 하며, to-one association에 특히 적합하다고 설명한다.

### 예시 3. repository 메서드 단위 eager 요구는 EntityGraph로 선언한다

```java
@Entity
@NamedEntityGraph(
    name = "User.withRoles",
    attributeNodes = {
        @NamedAttributeNode("roles")
    }
)
@Table(name = "users", schema = "auth")
public class User {
    // ...
}

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(value = "User.withRoles", type = EntityGraph.EntityGraphType.FETCH)
    Optional<User> findByEmail(String email);
}
```

**좋은 이유:**

- 정적 매핑을 EAGER로 바꾸지 않고, 특정 repository 메서드에서만 roles를 가져온다
- FETCH graph로 명시한 속성만 eager 범위에 넣는다
- entity graph를 fetch plan template로 사용하는 정석적인 방식이다

Jakarta Persistence는 fetchgraph와 loadgraph를 공식적으로 지원하고, Spring Data JPA는 repository method에서 @EntityGraph와 동적 attributePaths()를 지원한다.

### 예시 4. 여러 부모의 같은 LAZY 컬렉션 접근에는 BatchSize를 보조적으로 사용한다

```java
@Entity
@Table(name = "users", schema = "auth")
public class User {

    @Id
    private Long id;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<UserRole> roles = new ArrayList<>();
}
```

**좋은 이유:**

- 여러 사용자의 roles 컬렉션을 순차적으로 초기화할 때 round trip 수를 줄일 수 있다
- join fetch가 항상 적절하지 않은 경우의 보조 최적화다
- 기본 전략을 EAGER로 바꾸지 않는다

Hibernate는 @BatchSize가 여러 uninitialized association을 한 번에 가져오도록 도와주며, N+1보다 낫지만 대개 JOIN FETCH나 DTO projection이 더 좋다고 설명한다.

### 예시 5. 조회 전용 목록은 DTO projection으로 닫는다

```java
public record UserSummary(Long id, String email, String status) {}

@Query("""
    select new com.example.auth.user.UserSummary(u.id, u.email, u.status)
    from User u
    where u.deletedAt is null
    order by u.id desc
""")
List<UserSummary> findActiveUserSummaries();
```

**좋은 이유:**

- 수정 목적이 아닌 조회에서 엔티티 그래프 전체를 관리하지 않는다
- 필요한 컬럼만 선택한다
- persistence context 부담을 줄이고 fetch 전략 고민도 단순화한다

Hibernate는 read-only transaction에는 DTO projection이 더 적합하고, 필요한 컬럼만 조회할 수 있어 persistence context 부담을 줄인다고 설명한다.

## 나쁜 예시

### 예시 1. to-one 기본값을 그대로 두어 암묵적 EAGER를 만든다

```java
@Entity
@Table(name = "sessions", schema = "auth")
public class Session {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

**나쁜 이유:**

- @ManyToOne 기본값은 EAGER라서, 의도하지 않아도 user를 항상 가져오게 된다
- query별 fetch 정책을 세밀하게 제어하기 어려워진다
- JPQL에서 fetch join을 빠뜨리면 secondary select로 이어질 수 있다

Jakarta Persistence는 @ManyToOne 기본 fetch를 EAGER로 정의하고, Hibernate는 이런 EAGER 기본값을 피하라고 권장한다.

### 예시 2. 여러 컬렉션을 한 query에서 동시에 fetch join한다

```java
@Query("""
    select u
    from User u
    left join fetch u.roles
    left join fetch u.sessions
    left join fetch u.loginHistories
    where u.id = :id
""")
Optional<User> findEverything(@Param("id") Long id);
```

**나쁜 이유:**

- 여러 to-many를 병렬 fetch join하면 Cartesian product가 발생할 수 있다
- row 폭발과 중복으로 성능이 매우 나빠질 수 있다
- “한 번에 다 가져오자”는 발상이 오히려 가장 위험하다

Hibernate는 여러 컬렉션/to-many를 병렬 fetch join하면 Cartesian product가 생겨 매우 나쁜 성능을 낼 수 있다고 명시한다.

### 예시 3. pagination query에 컬렉션 fetch join을 사용한다

```java
@Query("""
    select u
    from User u
    left join fetch u.roles
    order by u.id desc
""")
Page<User> findAllWithRoles(Pageable pageable);
```

**나쁜 이유:**

- 컬렉션 fetch join과 pagination은 충돌하기 쉽다
- page boundary가 불안정해지고 row duplication 문제가 생길 수 있다
- 목록/페이지 조회는 다른 방식으로 설계해야 한다

Hibernate는 fetch join을 제한/페이징 query에서 보통 피해야 한다고 설명한다.

### 예시 4. 지연 로딩 문제를 presentation 계층에서 우연히 해결되길 기대한다

```java
@Transactional(readOnly = true)
public User getUser(Long id) {
    return userRepository.findById(id).orElseThrow();
}

// controller
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable Long id) {
    User user = userService.getUser(id);
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getRoles().stream().map(UserRole::getName).toList()
    );
}
```

**나쁜 이유:**

- service 트랜잭션이 끝난 뒤 controller에서 lazy 컬렉션 접근을 시도한다
- 환경에 따라 LazyInitializationException 또는 우연한 추가 SQL에 의존하게 된다
- fetch plan 책임이 service/application 경계 밖으로 새어 나간다

Hibernate는 필요한 연관은 persistence context가 닫히기 전에 fetch해야 하며, 그렇지 않으면 LazyInitializationException이 발생한다고 설명한다.

### 예시 5. read-only 목록인데도 엔티티 전체를 억지로 로딩한다

```java
@Query("""
    select u
    from User u
    left join fetch u.department
    left join fetch u.roles
    where u.deletedAt is null
    order by u.id desc
""")
List<User> findAllForAdminList();
```

**나쁜 이유:**

- 목록 화면에 필요한 필드보다 훨씬 많은 엔티티 상태를 가져오기 쉽다
- 관리 대상 엔티티 수와 SQL row 수가 불필요하게 커진다
- 이런 경우는 DTO projection이 더 적합할 가능성이 높다

Hibernate는 read-only transaction에서는 DTO projection이 더 적절하고, 필요한 컬럼만 선택할 수 있다고 설명한다.

### 예시 6. @BatchSize로 구조 문제를 덮는다

```java
@Entity
public class User {

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @BatchSize(size = 1000)
    private List<UserRole> roles;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @BatchSize(size = 1000)
    private List<Session> sessions;
}
```

**나쁜 이유:**

- 왜 secondary select 구조가 필요한지 설명 없이 숫자만 크게 올린다
- batch fetching은 보조 최적화이지 1차 설계 수단이 아니다
- query 설계, DTO projection, entity graph 같은 더 직접적인 해법을 가릴 수 있다

Hibernate도 @BatchSize는 N+1보다 낫지만 대개 JOIN FETCH나 DTO projection이 더 좋은 선택이라고 설명한다.
