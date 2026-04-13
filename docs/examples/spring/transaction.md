# @Transactional 위치 예시

## 좋은 예시 1: use case 경계에 transaction

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

    @Transactional
    public UserId register(CreateUserCommand command) {
        if (userReader.findByEmail(UserEmail.from(command.email())).isPresent()) {
            throw new DuplicateUserException();
        }

        User user = User.create(
                UserEmail.from(command.email()),
                UserName.from(command.name()),
                passwordHasher.hash(command.password())
        );

        return userAppender.append(user);
    }
}
```

**왜 좋은가:**

- 비즈니스 작업 단위가 transaction 경계와 일치한다
- repository 호출들이 하나의 원자적 작업으로 묶인다
- controller나 repository에 흩어지지 않는다

## 좋은 예시 2: 조회 use case는 readOnly

```java
@Service
public class GetUserProfileService implements GetUserProfileUseCase {

    private final UserReader userReader;

    public GetUserProfileService(UserReader userReader) {
        this.userReader = userReader;
    }

    @Transactional(readOnly = true)
    public UserProfileResult get(UserId userId) {
        User user = userReader.findById(userId).orElseThrow(UserNotFoundException::new);
        return UserProfileResult.from(user);
    }
}
```

**왜 좋은가:**

- 순수 조회라는 의도가 드러난다
- 쓰기 작업과 구분된다

## 좋은 예시 3: 별도 확정 단위가 필요한 경우만 REQUIRES_NEW

```java
@Service
public class AuditLogService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LoginAuditCommand command) {
        ...
    }
}
```

**왜 좋은가:**

- 본 작업과 독립된 commit 단위를 의도적으로 분리한다
- 예외적 사용이라는 점이 분명하다

## 나쁜 예시 1: controller에 transaction

```java
@RestController
public class UserController {

    @Transactional
    @PostMapping("/users")
    public UserResponse create(@RequestBody CreateUserRequest request) {
        ...
    }
}
```

**문제:**

- HTTP 경계와 transaction 경계가 섞인다
- web layer가 persistence 세부를 과도하게 끌어안는다

## 나쁜 예시 2: repository마다 습관적 transaction

```java
@Repository
public class JpaUserRepository {

    @Transactional
    public UserJpaEntity save(UserJpaEntity entity) {
        ...
    }
}
```

**문제:**

- 상위 use case 경계가 아니라 하위 collaborator에 transaction이 흩어진다
- 작업 단위가 잘게 찢어진다

## 나쁜 예시 3: self-invocation 기대

```java
@Service
public class UserService {

    public void doWork() {
        this.saveAudit(); // transactional 기대
    }

    @Transactional
    public void saveAudit() {
        ...
    }
}
```

**문제:**

- proxy mode에서는 self-invocation이 interception 되지 않는다
- 기대한 transaction이 실제로 열리지 않을 수 있다

**개선:**

- 클래스를 분리하거나 public entry boundary를 다시 설계

## 나쁜 예시 4: 긴 외부 API 호출을 transaction 안에 유지

```java
@Transactional
public void completeLogin(LoginCommand command) {
    userRepository.save(...);
    externalOAuthClient.fetchProfile(...); // 긴 네트워크 호출
    tokenRepository.save(...);
}
```

**문제:**

- DB 자원/잠금을 오래 붙잡을 수 있다
- 실패 반경과 지연 시간이 커진다

**개선 방향:**

- 외부 호출과 DB transaction 경계를 재설계
- 후속 작업/event/outbox 구조 검토
