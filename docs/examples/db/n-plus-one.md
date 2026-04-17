# N+1 예시

## 좋은 예시

### 예시 1. 상세 조회의 to-one 연관은 join fetch로 한 번에 가져온다

```java
@Query("""
    select s
    from Session s
    join fetch s.user
    where s.id = :id
""")
Optional<Session> findDetailById(@Param("id") Long id);
```

**좋은 이유:**

- Session 상세 조회에서 필요한 user를 같은 query로 가져온다
- to-one 연관에 대한 N+1 위험을 가장 직접적으로 제거한다
- 기본 매핑은 LAZY로 유지하면서, 이 use case에서만 eager 요구를 적용한다

Hibernate는 JOIN FETCH가 @ManyToOne / @OneToOne에 좋고, 필요한 association은 persistence context가 닫히기 전에 fetch하는 것이 가장 좋은 대응이라고 설명한다.

### 예시 2. query 단위 fetch plan은 EntityGraph로 선언한다

```java
@Entity
@NamedEntityGraph(
    name = "User.withRoles",
    attributeNodes = @NamedAttributeNode("roles")
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

- 정적 매핑을 EAGER로 바꾸지 않고, 특정 repository method에서만 roles를 함께 가져온다
- fetch plan이 use case 단위로 분리된다
- named graph를 재사용할 수 있다

Jakarta Persistence는 fetchgraph와 loadgraph를 표준으로 정의하고, Spring Data JPA는 repository method에 @EntityGraph를 붙여 fetch/load graph를 적용할 수 있다고 설명한다.

### 예시 3. read-only 목록은 DTO projection으로 닫는다

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

- 목록 조회에서 엔티티와 연관 그래프 전체를 관리하지 않는다
- 필요한 컬럼만 가져오므로 N+1 설계 자체를 피한다
- 가장 단순하고 예측 가능한 목록 조회 구조다

Hibernate는 @BatchSize보다 DTO projection이나 JOIN FETCH가 더 좋은 대안인 경우가 많다고 설명한다. 특히 read-only 목록에서는 DTO projection이 더 신뢰도 높은 선택이다.

### 예시 4. 여러 부모의 같은 컬렉션을 뒤이어 접근한다면 BatchSize를 보조적으로 사용한다

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

- 여러 User의 roles 컬렉션을 순차적으로 초기화할 때 secondary select를 묶어 줄 수 있다
- N+1을 완화하지만, 기본 fetch를 EAGER로 바꾸지 않는다
- 보조 최적화로서 의미가 분명하다

Hibernate는 @BatchSize가 여러 연관을 single database round trip 또는 적은 수의 round trip으로 묶을 수 있다고 설명한다. 다만 DTO projection이나 JOIN FETCH가 더 좋은 대안인 경우가 많다고도 함께 설명한다.

### 예시 5. 페이징 목록은 ID 페이지 조회와 후속 query를 분리한다

```java
@Query("""
    select u.id
    from User u
    where u.deletedAt is null
    order by u.createdAt desc, u.id desc
""")
Page<Long> findUserIds(Pageable pageable);

@Query("""
    select distinct u
    from User u
    left join fetch u.department
    where u.id in :ids
""")
List<User> findUsersWithDepartment(@Param("ids") Collection<Long> ids);
```

**좋은 이유:**

- page boundary는 root ID query에서 안정적으로 결정한다
- 후속 query는 page 범위 안에서만 필요한 연관을 가져온다
- 컬렉션 fetch join + pagination 충돌을 피하면서 N+1도 막는다

Hibernate는 paged query에서 fetch join, 특히 many-valued association에 대한 fetch join을 피하라고 설명한다. 이 분리 전략은 해당 제약을 피해 가는 실무 best practice다.

## 나쁜 예시

### 예시 1. N+1을 막겠다며 to-one을 EAGER로 선언한다

```java
@Entity
@Table(name = "sessions", schema = "auth")
public class Session {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

**나쁜 이유:**

- 이 설정은 모든 query에서 user 로딩 비용을 고정한다
- JPQL query에서 fetch join을 빠뜨리면 오히려 secondary select가 association마다 발생해 N+1로 이어질 수 있다
- 문제를 매핑에 숨기고 use case 단위 제어를 잃는다

Hibernate는 EAGER fetching is almost always a bad choice라고 설명하고, EAGER association을 query에서 join fetch하지 않으면 secondary select로 N+1이 생길 수 있다고 명시한다.

### 예시 2. 루트 목록을 가져온 뒤 컬렉션을 루프에서 접근한다

```java
List<User> users = entityManager.createQuery("""
    select u
    from User u
    where u.deletedAt is null
    order by u.id desc
""", User.class).getResultList();

for (User user : users) {
    user.getRoles().size();
}
```

**나쁜 이유:**

- root query 1번 뒤에, user 수만큼 roles 초기화 SQL이 반복될 수 있다
- 가장 전형적인 N+1 구조다
- 목록 조회라면 DTO projection, join fetch, batch fetching 등으로 구조를 바꿔야 한다

Hibernate는 collection을 first access 시 secondary select로 초기화하는 FetchMode.SELECT가 N+1을 일으킬 수 있다고 설명한다.

### 예시 3. 여러 컬렉션을 한 query에서 병렬 fetch join한다

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

- 여러 to-many/collection을 동시에 fetch join하면 Cartesian product가 발생할 수 있다
- 중복 row가 폭증하고 성능이 매우 나빠질 수 있다
- “한 번에 다 가져오기”가 오히려 더 위험하다

Hibernate는 multiple collections or to-many associations in parallel fetch join results in a Cartesian product and might exhibit very poor performance라고 명시한다.

### 예시 4. pagination query에 컬렉션 fetch join을 사용한다

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

- many-valued association fetch join과 pagination이 충돌한다
- limit가 DB가 아니라 메모리에서 적용될 수 있다
- page boundary와 성능이 모두 불안정해진다

Hibernate는 fetch joins should usually be avoided in limited or paged queries라고 설명하고, 컬렉션 fetch join과 pagination 조합은 terrible performance characteristics를 만들 수 있다고 경고한다.

### 예시 5. 트랜잭션 밖에서 lazy 연관이 알아서 초기화되기를 기대한다

```java
@Transactional(readOnly = true)
public User getUser(Long id) {
    return userRepository.findById(id).orElseThrow();
}

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

- service 트랜잭션 종료 후 controller에서 lazy collection 접근을 시도한다
- 환경에 따라 LazyInitializationException이 발생하거나, 우연한 추가 query에 의존할 수 있다
- fetch plan 책임이 presentation 계층으로 새어 나간다

Hibernate는 필요한 association은 persistence context가 닫히기 전에 fetch하는 것이 가장 좋은 대응이라고 설명한다.

### 예시 6. BatchSize만 크게 올려 구조 문제를 덮는다

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

- 왜 secondary select 구조를 유지하는지 설명 없이 숫자만 키운다
- batch fetching은 보조 완화 수단이지 1차 설계 수단이 아니다
- DTO projection, join fetch, query 분리 같은 더 직접적인 해결책을 가릴 수 있다

Hibernate도 @BatchSize는 N+1보다 낫지만, 대부분의 경우 DTO projection이나 JOIN FETCH가 더 좋은 대안이라고 설명한다.
