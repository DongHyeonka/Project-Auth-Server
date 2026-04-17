# Mock 사용 예시

## 좋은 예시

### 예시 1. 순수 단위 테스트에서는 MockitoExtension과 @Mock를 사용한다

```java
@ExtendWith(MockitoExtension.class)
class UserNotifierTest {

    @Mock
    private MailSender mailSender;

    private UserNotifier userNotifier;

    @BeforeEach
    void setUp() {
        userNotifier = new UserNotifier(mailSender);
    }

    @Test
    void sendWelcomeMail_delegatesToMailSender() {
        userNotifier.sendWelcomeMail("a@test.com");

        verify(mailSender).send("a@test.com");
    }
}
```

**좋은 이유:**

- Spring 컨텍스트 없이 외부 협력자만 대체한다
- 테스트 대상 생성이 명시적이라 의존성이 잘 드러난다
- interaction verification도 외부 경계에만 한정된다

Mockito는 `MockitoExtension`이 JUnit Jupiter용 확장이고 strict stubbings를 처리한다고 설명한다. `@InjectMocks`는 편의 기능이지만, 프로젝트 기본값은 명시적 생성자 조립을 우선한다.

### 예시 2. @InjectMocks는 보조 편의 수단으로 제한적으로 사용한다

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findUser_returnsRepositoryResult() {
        User user = new User(1L, "a@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Optional<User> result = userService.findUser(1L);

        assertThat(result).contains(user);
    }
}
```

**좋은 이유:**

- 대상이 단순하고 의존성도 적을 때 보일러플레이트를 줄일 수 있다
- `@InjectMocks`를 “자동 wiring 마법”이 아니라 편의 기능으로만 사용한다
- 테스트의 핵심 stub과 assertion은 여전히 본문에 남아 있다

Mockito는 `@InjectMocks`가 constructor/property/setter injection 순서로 mock 주입을 시도한다고 설명한다.

### 예시 3. Spring 컨텍스트 테스트에서 bean 하나만 대체할 때는 @MockitoBean을 사용한다

```java
@SpringBootTest
class PaymentFacadeTest {

    @MockitoBean
    private PaymentGatewayClient paymentGatewayClient;

    @Autowired
    private PaymentFacade paymentFacade;

    @Test
    void approve_usesGatewayClient() {
        when(paymentGatewayClient.approve(any())).thenReturn(new GatewayResult(true));

        boolean result = paymentFacade.approve(1L);

        assertThat(result).isTrue();
    }
}
```

**좋은 이유:**

- full context가 필요한 테스트에서 특정 bean만 override한다
- 신규 기준에 맞는 `@MockitoBean`을 사용한다
- 외부 연동 경계만 mock으로 대체한다

Spring Framework는 `@MockitoBean`이 테스트 `ApplicationContext`의 bean을 Mockito mock으로 override한다고 설명하고, Spring Boot도 이를 공식 테스트 기능으로 안내한다.

### 예시 4. spy가 꼭 필요하면 doReturn(...).when(spy)...를 사용한다

```java
@ExtendWith(MockitoExtension.class)
class LegacyUserServiceTest {

    @Test
    void spy_stubsWithoutCallingRealMethod() {
        LegacyUserService spy = spy(new LegacyUserService());

        doReturn("stubbed").when(spy).loadExternalValue();

        String result = spy.read();

        assertThat(result).isEqualTo("stubbed");
    }
}
```

**좋은 이유:**

- spy가 필요한 예외 상황에서도 실제 메서드 부작용을 피한다
- Mockito가 권장하는 spy stubbing 방식과 맞다
- partial mock을 최소 범위로 제한한다

Mockito는 spy를 carefully and occasionally 사용하라고 설명하고, spy stubbing에는 `doReturn` 계열을 고려하라고 설명한다.

### 예시 5. verifyNoMoreInteractions()는 정말 의미가 있을 때만 쓴다

```java
@ExtendWith(MockitoExtension.class)
class AuditPublisherTest {

    @Mock
    private EventBus eventBus;

    @Test
    void publishExactlyOneAuditEvent() {
        AuditPublisher publisher = new AuditPublisher(eventBus);

        publisher.publish("LOGIN");

        verify(eventBus).publish("LOGIN");
        verifyNoMoreInteractions(eventBus);
    }
}
```

**좋은 이유:**

- “정확히 한 번만 발행되어야 한다”는 의미가 테스트 요구와 직접 연결된다
- 습관적 사용이 아니라 비즈니스 의미가 있을 때만 사용한다
- interaction assertion이 과도하지 않다

Mockito는 `verifyNoMoreInteractions()`를 every test method에 사용하는 것을 권장하지 않지만, interaction testing toolkit의 일부로는 유용하다고 설명한다.

## 나쁜 예시

### 예시 1. 테스트 대상 자체를 mock한다

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserService userService;

    @Test
    void findUser() {
        when(userService.findUser(1L)).thenReturn(Optional.of(new User(1L, "a@test.com")));
    }
}
```

**나쁜 이유:**

- 검증하려는 대상을 아예 가짜로 바꿔 버린다
- 테스트가 대상 로직을 전혀 실행하지 않는다
- mock은 협력자 경계에만 써야 한다

Mockito mock은 협력 객체를 대체하는 도구이지, 테스트 대상을 없애는 도구가 아니다. Spring의 `@MockitoBean`도 마찬가지로 bean override 용도다.

### 예시 2. repository test에서 repository를 mock한다

```java
@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void findByEmail() {
        when(userRepository.findByEmail("a@test.com"))
                .thenReturn(Optional.of(new User(1L, "a@test.com")));
    }
}
```

**나쁜 이유:**

- repository 경계의 실제 DB 의미, query, 매핑을 전혀 검증하지 않는다
- 이런 테스트는 repository test가 아니라 service 단위 테스트의 협력자 stub에 가깝다
- repository 자체 검증은 실제 repository/DB로 해야 한다

repository test의 목적은 영속성 경계 검증이므로 mock repository는 목적과 맞지 않는다.

### 예시 3. 모든 테스트에 verifyNoMoreInteractions()를 습관적으로 붙인다

```java
@ExtendWith(MockitoExtension.class)
class UserNotifierTest {

    @Mock
    private MailSender mailSender;

    @Test
    void sendWelcomeMail() {
        UserNotifier notifier = new UserNotifier(mailSender);

        notifier.sendWelcomeMail("a@test.com");

        verify(mailSender).send("a@test.com");
        verifyNoMoreInteractions(mailSender);
    }
}
```

**나쁜 이유:**

- 추가 상호작용 금지가 이 테스트의 핵심 의미가 아닐 수도 있다
- 테스트가 불필요하게 취약해진다
- Mockito도 이 메서드를 every test method에 쓰는 것은 권장하지 않는다

Mockito는 `verifyNoMoreInteractions()`를 모든 테스트마다 쓰는 것을 권장하지 않는다고 설명한다.

### 예시 4. Spring 신규 테스트에서 @MockBean을 기본값으로 쓴다

```java
@SpringBootTest
class PaymentFacadeTest {

    @MockBean
    private PaymentGatewayClient paymentGatewayClient;
}
```

**나쁜 이유:**

- 현재 Spring 기준에서는 신규 코드 기본값이 아니다
- Spring Boot는 `@MockBean`이 3.4.0부터 deprecated 되었고 `@MockitoBean`을 쓰라고 안내한다
- 장기적으로 제거 예정 API에 새 테스트를 얹는 셈이다

Spring Boot API 문서는 `@MockBean`이 3.4.0부터 4.0.0 제거 예정으로 deprecated 되었고 `MockitoBean`을 대안으로 제시한다.

### 예시 5. spy에서 when(spy.method())로 실제 메서드를 먼저 호출한다

```java
@ExtendWith(MockitoExtension.class)
class LegacyUserServiceTest {

    @Test
    void badSpyUsage() {
        LegacyUserService spy = spy(new LegacyUserService());

        when(spy.loadExternalValue()).thenReturn("stubbed");
    }
}
```

**나쁜 이유:**

- stub 과정에서 실제 메서드가 호출될 수 있다
- 부작용이나 예외를 일으킬 수 있다
- spy에서는 `doReturn(...).when(spy)...`가 더 안전하다

Mockito는 spy stubbing에서 `when(...)`가 부적절할 수 있고, `doReturn` 계열을 고려하라고 설명한다.

### 예시 6. static mock을 길게 열어 두고 일반 테스트처럼 사용한다

```java
@Test
void badStaticMockUsage() {
    MockedStatic<ClockUtil> mocked = mockStatic(ClockUtil.class);
    mocked.when(ClockUtil::now).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));

    // 여러 로직 수행
}
```

**나쁜 이유:**

- scope가 길고 close가 명확하지 않다
- static mock은 생성된 thread에만 영향을 주고 동시 사용에도 안전하지 않다
- try-with-resources로 짧게 감싸는 편이 맞다

Mockito는 `MockedStatic`이 생성된 thread에만 영향을 주며 concurrent use에 안전하지 않다고 설명한다.

### 예시 7. non-singleton bean을 무심코 @MockitoBean으로 바꾼다

```java
@SpringBootTest
class ScopedBeanTest {

    @MockitoBean
    private RequestScopedClient requestScopedClient;
}
```

**나쁜 이유:**

- Spring은 non-singleton bean을 mock/spy하면 singleton처럼 취급될 수 있다고 설명한다
- scope 의미가 깨질 수 있다
- 이런 경우는 테스트 구조 자체를 다시 설계하는 편이 더 안전하다

Spring Framework는 non-singleton bean을 `@MockitoBean`/`@MockitoSpyBean`으로 override하면 singleton처럼 다뤄질 수 있다고 설명한다.
