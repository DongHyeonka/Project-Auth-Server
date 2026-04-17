# Fixture / Factory 예시

## 좋은 예시

### 예시 1. factory는 새 유효 객체를 매번 반환한다

```java
public final class UserFactory {

    private UserFactory() {
    }

    public static User user() {
        return new User(
                "user-" + UUID.randomUUID() + "@test.com",
                "ACTIVE"
        );
    }

    public static User user(UnaryOperator<UserBuilder> customizer) {
        UserBuilder builder = UserBuilder.defaultUser();
        return customizer.apply(builder).build();
    }
}
```

**좋은 이유:**

- 매 호출마다 새 객체를 만든다
- 기본값은 유효한 상태다
- 테스트는 필요한 값만 override할 수 있다

JUnit은 기본적으로 테스트 메서드마다 새 테스트 인스턴스를 만들어 격리를 보장하려고 하므로, 테스트 데이터 helper도 같은 방향으로 fresh object를 주는 것이 자연스럽다.

### 예시 2. fixture 이름이 시나리오를 설명한다

```java
public final class UserFixture {

    private UserFixture() {
    }

    public static User activeUser() {
        return UserFactory.user();
    }

    public static User deletedUser() {
        return UserFactory.user(builder -> builder.deletedAt(OffsetDateTime.now()));
    }

    public static User invalidEmailUser() {
        return UserFactory.user(builder -> builder.email("not-an-email"));
    }
}
```

**좋은 이유:**

- fixture 이름만 봐도 상태 의미가 드러난다
- invalid 상태도 명시적으로 분리된다
- factory와 fixture의 역할이 나뉜다

이런 분리는 공식 어노테이션이 강제하는 것은 아니지만, Spring 테스트 지원이 fixture 준비를 쉽게 해 주는 목적과 잘 맞는 실무 패턴이다.

### 예시 3. repository test에서는 persisted fixture를 분리한다

```java
@Component
@RequiredArgsConstructor
public class PersistedUserFactory {

    private final EntityManager em;

    public User persistedUser() {
        User user = UserFactory.user();
        em.persist(user);
        em.flush();
        return user;
    }

    public User persistedUserAndClear() {
        User user = UserFactory.user();
        em.persist(user);
        em.flush();
        em.clear();
        return user;
    }
}
```

**좋은 이유:**

- DB에 반영된 fixture와 메모리 상태 fixture를 구분한다
- repository test에서 영속성 상태를 더 명확히 다룰 수 있다
- 표준 `EntityManager`만 사용하므로 `@DataJpaTest`와 `@SpringBootTest` 양쪽 컨텍스트에서 동일하게 동작한다

Spring Boot는 `@DataJpaTest` 슬라이스에서 `TestEntityManager`를 `persist`/`flush`/`find` 같은 common testing task를 위한 대안 `EntityManager`로 제공한다고 설명한다. 다만 `TestEntityManager`는 `@DataJpaTest` 컨텍스트에서만 자동 구성되므로, `@SpringBootTest`에서도 재사용할 helper에는 표준 `EntityManager`를 주입하는 것이 안전하다.

### 예시 4. 핵심 차이는 테스트 본문에 남긴다

```java
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_returnsMatchingUser() {
        User saved = userRepository.save(
                UserFactory.user(builder -> builder.email("target@test.com"))
        );

        Optional<User> result = userRepository.findByEmail("target@test.com");

        assertThat(result).contains(saved);
    }
}
```

**좋은 이유:**

- 반복 필드는 factory가 채우지만, 핵심 조건인 email은 테스트 본문에 드러난다
- 테스트를 읽는 사람이 왜 이 테스트가 중요한지 바로 이해할 수 있다
- fixture/factory가 assertion의 핵심을 숨기지 않는다

Spring 테스트 문서는 DI와 테스트 지원이 테스트를 더 쉽게 만들 수 있다고 설명하지만, 그 목적은 테스트 의미를 감추는 것이 아니다.

### 예시 5. Spring context 테스트에서도 fixture helper는 test support로 분리한다

```java
@SpringBootTest
class UserCommandServiceTest {

    @Autowired
    private UserCommandService userCommandService;

    @Autowired
    private PersistedUserFactory persistedUserFactory;

    @Test
    void deactivateUser_marksUserInactive() {
        User user = persistedUserFactory.persistedUser();

        userCommandService.deactivate(user.getId());

        // assertion ...
    }
}
```

**좋은 이유:**

- fixture 준비가 재사용 가능하다
- 그래도 테스트의 핵심 동작은 서비스 호출과 assertion에 남아 있다
- Spring DI는 helper 주입에만 쓰고, fixture 자체를 production 로직처럼 다루지 않는다

Spring Framework는 테스트 인스턴스에 field, setter, constructor injection을 지원한다고 설명한다.

## 나쁜 예시

### 예시 1. mutable shared fixture를 static으로 재사용한다

```java
public final class SharedFixtures {
    public static final User USER = new User("a@test.com", "ACTIVE");
}
```

**나쁜 이유:**

- 한 테스트의 변경이 다른 테스트에 영향을 줄 수 있다
- JUnit의 기본 per-method 격리 철학과 맞지 않는다
- 테스트 순서 의존과 flaky test를 만들기 쉽다

JUnit은 기본 lifecycle이 테스트 간 mutable state 부작용을 피하기 위한 `PER_METHOD`라고 설명한다.

### 예시 2. boolean 나열형 factory로 의미를 숨긴다

```java
public static User user(boolean deleted, boolean admin, boolean locked, boolean invalidEmail) {
    // ...
}
```

**나쁜 이유:**

- 호출부에서 각 boolean이 무엇을 뜻하는지 바로 알기 어렵다
- 상태 의미가 시나리오 이름으로 드러나지 않는다
- 잘못된 조합도 쉽게 생긴다

이런 형태는 fixture/factory가 테스트 가독성을 높여야 한다는 목적에 어긋난다. 프로젝트에서는 명시적 이름의 fixture나 builder override를 선호한다.

### 예시 3. invalid 상태를 기본 factory에 섞는다

```java
public static User user() {
    return new User(null, "ACTIVE");
}
```

**나쁜 이유:**

- 기본 factory가 유효하지 않은 객체를 만든다
- 여러 테스트가 뜻하지 않게 invalid 상태를 끌고 들어온다
- invalid 검증 테스트와 정상 경로 테스트가 섞인다

factory 기본값은 특별한 이유가 없으면 유효한 객체여야 테스트 의도가 분명해진다. 이는 프로젝트 fixture/factory 기본 규칙이다.

### 예시 4. repository test에서 DB round-trip 의미를 helper가 완전히 숨긴다

```java
public User persistedUser() {
    User user = UserFactory.user();
    em.persist(user);
    em.flush();
    em.clear();
    return em.find(User.class, user.getId());
}
```

**나쁜 이유:**

- 언제 `flush`/`clear`가 일어나는지 테스트 본문에서 보이지 않는다
- 어떤 테스트는 `flush`까지만 필요하고, 어떤 테스트는 `clear`가 핵심인데 모두 같은 helper 뒤에 숨는다
- repository semantics를 읽기 어렵게 만든다

`TestEntityManager`는 helper를 제공하지만, 그 목적은 테스트를 보조하는 것이지 중요한 JPA 의미를 완전히 숨기는 것이 아니다.

### 예시 5. fixture/factory를 production source에 넣는다

```java
src/main/java/com/example/user/UserFixture.java
```

**나쁜 이유:**

- 테스트 지원 코드가 production code와 경계를 잃는다
- 실제 애플리케이션 책임과 테스트 전용 책임이 섞인다
- 유지보수 시 production API처럼 오해되기 쉽다

Spring 테스트 문서는 테스트 인스턴스에 대한 DI를 지원하지만, 테스트 준비 코드를 production source에 두라고 요구하지는 않는다. 프로젝트에서는 test support를 test source에 두는 것이 기본이다.

### 예시 6. PER_CLASS lifecycle에 기대어 상태를 공유한다

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserRepositoryTest {

    private final List<User> users = new ArrayList<>();

    @Test
    void test1() {
        users.add(UserFactory.user());
    }

    @Test
    void test2() {
        assertThat(users).hasSize(1);
    }
}
```

**나쁜 이유:**

- 테스트 간 상태가 공유된다
- 순서와 실행 방식에 따라 쉽게 깨질 수 있다
- fixture 편의 때문에 lifecycle을 바꾼 나쁜 예다

JUnit은 `PER_CLASS`를 쓰면 instance state를 직접 reset해야 할 수 있고, 기본 lifecycle을 일관되지 않게 바꾸면 fragile build가 될 수 있다고 경고한다.
