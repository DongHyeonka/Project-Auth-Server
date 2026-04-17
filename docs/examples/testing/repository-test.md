# Repository Test 예시

## 좋은 예시

### 예시 1. 기본 repository test는 @DataJpaTest로 시작한다

```java
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_returnsUser() {
        // given
        User user = new User("a@test.com", "active");
        userRepository.save(user);

        // when
        Optional<User> result = userRepository.findByEmail("a@test.com");

        // then
        assertThat(result).isPresent();
    }
}
```

**좋은 이유:**

- JPA slice만 좁게 로딩한다
- repository 자체를 테스트 대상으로 유지한다
- full application context를 불필요하게 띄우지 않는다

Spring Boot는 `@DataJpaTest`가 JPA components에 초점을 맞추고, 엔티티와 repository를 스캔한다고 설명한다.

### 예시 2. 제약 위반은 flush()까지 가서 검증한다

```java
@DataJpaTest
class UserRepositoryConstraintTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_duplicateEmail_throwsExceptionOnFlush() {
        userRepository.save(new User("dup@test.com", "active"));
        userRepository.save(new User("dup@test.com", "active"));

        assertThatThrownBy(() -> userRepository.flush())
                .isInstanceOf(Exception.class);
    }
}
```

**좋은 이유:**

- unique constraint 위반이 실제 DB 동기화 시점에 드러난다는 점을 반영한다
- `save()`만 보고 통과한 테스트를 방지한다
- 영속성 경계의 실제 실패 시점을 검증한다

Hibernate의 영속성 컨텍스트는 1차 캐시로 동작하므로, DB 의미를 보려면 `flush`가 중요하다. `TestEntityManager`도 `persistAndFlush` 같은 helper를 제공한다.

### 예시 3. 실제 재조회 의미를 검증할 때는 flush() 후 clear()를 사용한다

```java
@DataJpaTest
class UserRepositoryReloadTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void findActiveUsers_excludesDeletedRows() {
        userRepository.save(new User("a@test.com", "active", null));
        userRepository.save(new User("b@test.com", "active", OffsetDateTime.now()));

        em.flush();
        em.clear();

        List<User> users = userRepository.findAllByDeletedAtIsNullOrderByIdDesc();

        assertThat(users).extracting(User::getEmail)
                .containsExactly("a@test.com");
    }
}
```

**좋은 이유:**

- 1차 캐시가 아니라 실제 DB round-trip 이후 결과를 검증한다
- soft delete predicate 같은 조회 semantics를 더 신뢰도 높게 확인한다
- `TestEntityManager`를 보조 도구로만 사용한다

Hibernate는 persistence context가 transaction-scoped first-level cache로 동작한다고 설명하고, `TestEntityManager`는 test에서 `persist`/`flush`/`find` helper를 제공한다고 설명한다.

### 예시 4. PostgreSQL 특화 query는 실제 DB 계열에서 검증한다

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresUserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findRecentlyCreatedUsers_worksWithPostgresSpecificQuery() {
        // given / when / then
    }
}
```

**좋은 이유:**

- 임베디드 DB 대체를 끄고 실제 DB 계열을 사용하도록 의도를 드러낸다
- PostgreSQL native query나 dialect 의존 쿼리를 더 신뢰도 높게 검증할 수 있다
- repository test의 범위는 유지하면서 DB 의미를 맞춘다

Spring Boot는 `@DataJpaTest`가 기본적으로 임베디드 DB를 구성할 수 있고, 실제 DB를 선호하면 `@AutoConfigureTestDatabase`로 제어할 수 있다고 설명한다.

### 예시 5. commit이 정말 필요한 경우에만 예외적으로 commit 테스트를 쓴다

```java
@DataJpaTest
@Commit
class UserRepositoryCommitTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesDataThatMustBeObservedAfterCommit() {
        userRepository.save(new User("commit@test.com", "active"));
    }
}
```

**좋은 이유:**

- 기본 rollback 규칙을 알고, 필요한 경우에만 예외를 사용한다
- commit 이후에만 보이는 DB 효과를 검증하는 목적이 분명하다
- rollback이 기본, commit은 예외라는 기준을 지킨다

Spring 테스트는 transactional test를 기본 rollback하고, `@Commit`/`@Rollback`으로 이를 바꿀 수 있다고 설명한다.

## 나쁜 예시

### 예시 1. repository만 보는데 @SpringBootTest를 기본값으로 쓴다

```java
@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;
}
```

**나쁜 이유:**

- repository 경계만 볼 테스트에 full application context를 띄운다
- 테스트 범위와 비용이 과도하다
- `@DataJpaTest`가 더 적합한 기본값이다

Spring Boot는 `@DataJpaTest`를 JPA slice test로 제공하고, 일반 `@Component`는 로드하지 않는다고 설명한다.

### 예시 2. 제약 테스트를 flush 없이 작성한다

```java
@DataJpaTest
class UserRepositoryConstraintTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_duplicateEmail_throwsException() {
        userRepository.save(new User("dup@test.com", "active"));
        assertThatThrownBy(() ->
                userRepository.save(new User("dup@test.com", "active"))
        ).isInstanceOf(Exception.class);
    }
}
```

**나쁜 이유:**

- 실제 DB 제약 위반이 아직 드러나지 않을 수 있다
- 영속성 컨텍스트 안에서 테스트가 가짜로 통과하거나 실패 시점이 늦어질 수 있다
- 이런 검증은 보통 `flush` 시점까지 가야 한다

Hibernate의 persistence context는 1차 캐시로 동작하므로, DB 의미를 드러내려면 `flush`가 중요하다.

### 예시 3. 재조회 검증인데 clear() 없이 같은 엔티티만 본다

```java
@DataJpaTest
class UserRepositoryReloadTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findById_readsUpdatedState() {
        User user = userRepository.save(new User("a@test.com", "active"));
        user.changeStatus("inactive");

        User found = userRepository.findById(user.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo("inactive");
    }
}
```

**나쁜 이유:**

- 같은 persistence context 안의 같은 엔티티 인스턴스를 다시 본 것일 수 있다
- 실제 DB에서 다시 읽은 결과인지 보장되지 않는다
- 조회 semantics 검증으로는 신뢰도가 낮다

Hibernate는 `Session`/`EntityManager`가 first-level cache를 유지한다고 설명한다.

### 예시 4. PostgreSQL 특화 native query를 임베디드 DB만으로 신뢰한다

```java
@DataJpaTest
class NativePostgresQueryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void works() {
        userRepository.runPostgresSpecificQuery();
    }
}
```

**나쁜 이유:**

- 테스트 DB가 운영 DB 의미를 충분히 재현하지 못할 수 있다
- dialect 차이, 함수, JSONB, partial index 전제, locking clause 같은 부분은 놓치기 쉽다
- DB 특화 query는 실제 DB 계열 검증이 더 적합하다

Spring Boot는 `@DataJpaTest`가 임베디드 DB를 기본 구성할 수 있고, 실제 DB를 쓰려면 `@AutoConfigureTestDatabase`로 제어할 수 있다고 설명한다.

### 예시 5. repository test 안에서 service 정책까지 같이 검증한다

```java
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;
}
```

**나쁜 이유:**

- 테스트 범위가 repository 경계를 벗어난다
- slice 목적과 맞지 않는다
- service 정책 검증은 상위 통합 테스트 책임이다

`@DataJpaTest`는 JPA components에만 초점을 맞추고 일반 컴포넌트를 로드하지 않는 slice다.

### 예시 6. repository test에서 repository 대신 EntityManager만 직접 사용한다

```java
@DataJpaTest
class UserRepositoryTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void test() {
        em.persist(new User("a@test.com", "active"));
        em.createQuery("select u from User u", User.class).getResultList();
    }
}
```

**나쁜 이유:**

- repository를 검증하겠다면서 repository를 거의 사용하지 않는다
- 결국 repository contract가 아니라 JPA API 자체를 테스트하게 된다
- `EntityManager`는 보조 도구로만 쓰는 편이 적절하다

Spring Boot는 `TestEntityManager`를 repository/JPA test의 보조 도구로 제공한다고 설명한다. 중심은 repository여야 한다.
