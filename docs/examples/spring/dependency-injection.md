# dependency injection 예시

## 좋은 예시 1: 필수 의존성은 생성자 주입

```java
@Service
public class RegisterUserService implements RegisterUserUseCase {

    private final UserReader userReader;
    private final UserAppender userAppender;
    private final PasswordHasher passwordHasher;

    public RegisterUserService(
            UserReader userReader,
            UserAppender userAppender,
            PasswordHasher passwordHasher
    ) {
        this.userReader = userReader;
        this.userAppender = userAppender;
        this.passwordHasher = passwordHasher;
    }
}
```

**왜 좋은가:**

- 필수 의존성이 시그니처에 드러난다
- final field를 사용할 수 있다
- 객체가 완전한 상태로 생성된다

## 좋은 예시 2: single constructor면 @Autowired 생략

```java
@Component
public class JwtTokenIssuer {

    private final Clock clock;

    public JwtTokenIssuer(Clock clock) {
        this.clock = clock;
    }
}
```

**왜 좋은가:**

- Spring은 단일 생성자를 자동으로 사용할 수 있다
- annotation noise를 줄인다

## 좋은 예시 3: 선택 의존성은 setter/config method 검토

```java
@Component
public class AuditClient {

    private RetryTemplate retryTemplate = RetryTemplate.defaultInstance();

    @Autowired(required = false)
    public void setRetryTemplate(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
    }
}
```

**왜 좋은가:**

- 선택 의존성이라는 점이 드러난다
- reasonable default가 있다

## 좋은 예시 4: 다중 구현은 qualifier로 명시

```java
@Service
public class OAuthLoginService {

    private final OAuthClient googleOAuthClient;

    public OAuthLoginService(@Qualifier("googleOAuthClient") OAuthClient googleOAuthClient) {
        this.googleOAuthClient = googleOAuthClient;
    }
}
```

**왜 좋은가:**

- 여러 구현체 중 무엇을 주입받는지 명확하다
- 우연한 후보 선택에 기대지 않는다

## 나쁜 예시 1: production field injection

```java
@Service
public class RegisterUserService {

    @Autowired
    private UserReader userReader;

    @Autowired
    private UserAppender userAppender;
}
```

**문제:**

- 필수 의존성이 시그니처에 안 드러난다
- final field 사용이 어렵다
- plain unit test가 불편하다

## 나쁜 예시 2: service locator 사용

```java
@Service
public class RegisterUserService {

    @Autowired
    private ApplicationContext applicationContext;

    public void register(...) {
        UserAppender userAppender = applicationContext.getBean(UserAppender.class);
        ...
    }
}
```

**문제:**

- DI가 아니라 lookup으로 퇴행한다
- 숨은 의존성이 생긴다

## 나쁜 예시 3: 생성자 인자 과다를 setter로 숨김

```java
@Service
public class ComplexService {

    @Autowired
    public void setA(A a) { ... }

    @Autowired
    public void setB(B b) { ... }

    @Autowired
    public void setC(C c) { ... }

    @Autowired
    public void setD(D d) { ... }

    @Autowired
    public void setE(E e) { ... }

    @Autowired
    public void setF(F f) { ... }
}
```

**문제:**

- 책임이 큰 문제를 주입 방식으로 숨긴다
- 객체의 필수/선택 의존성이 흐려진다

**개선:**

- collaborator 분리
- orchestration 재설계
- 설정 묶기 검토
