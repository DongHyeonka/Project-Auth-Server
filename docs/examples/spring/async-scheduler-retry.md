# async / scheduler / retry 예시

## 좋은 예시

### 예시 1. 스케줄 트리거는 얇게 두고, 재시도는 외부 경계에 둔다

```java
@Component
@RequiredArgsConstructor
public class ExpiredSessionCleanupJob {

    private final ExpiredSessionCleanupUseCase expiredSessionCleanupUseCase;

    @Scheduled(cron = "${auth.session.cleanup-cron}")
    public void run() {
        expiredSessionCleanupUseCase.cleanUpExpiredSessions();
    }
}

@Service
@RequiredArgsConstructor
public class ExpiredSessionCleanupUseCase {

    private final SessionRepository sessionRepository;
    private final TokenRevocationGateway tokenRevocationGateway;
    private final CleanupAuditAsyncPublisher cleanupAuditAsyncPublisher;

    public void cleanUpExpiredSessions() {
        List<ExpiredSession> expiredSessions = sessionRepository.findExpiredSessions();

        for (ExpiredSession expiredSession : expiredSessions) {
            tokenRevocationGateway.revoke(expiredSession.tokenId());
        }

        cleanupAuditAsyncPublisher.publish(expiredSessions.size());
    }
}

@Component
public class TokenRevocationGateway {

    @Retryable(
            retryFor = {
                    ResourceAccessException.class,
                    SocketTimeoutException.class,
                    ConnectException.class
            },
            noRetryFor = {
                    IllegalArgumentException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, maxDelay = 2_000, multiplier = 2.0)
    )
    public void revoke(String tokenId) {
        // 외부 인증/폐기 시스템 호출
    }

    @Recover
    public void recover(Exception ex, String tokenId) {
        throw new ExternalDependencyException("Token revocation failed after retries. tokenId=" + tokenId, ex);
    }
}

@Component
@RequiredArgsConstructor
public class CleanupAuditAsyncPublisher {

    @Async("auditAsyncExecutor")
    public CompletableFuture<Void> publish(int cleanedCount) {
        // 감사 로그/알림 전송
        return CompletableFuture.completedFuture(null);
    }
}
```

**좋은 이유:**

- scheduler는 트리거만 담당
- retry는 외부 호출 경계에만 존재
- async는 비핵심 후속 처리로 분리
- 각 책임이 bean 경계로 나뉘어 프록시 적용 여부가 명확함

### 예시 2. executor / scheduler를 명시적으로 분리한다

```java
@Configuration
@EnableAsync
@EnableScheduling
@EnableRetry
public class TaskExecutionConfig implements AsyncConfigurer {

    @Bean(name = "auditAsyncExecutor")
    public ThreadPoolTaskExecutor auditAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("audit-async-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }

    @Bean(name = "maintenanceTaskScheduler")
    public ThreadPoolTaskScheduler maintenanceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("maintenance-scheduler-");
        scheduler.setPoolSize(2);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public Executor getAsyncExecutor() {
        return auditAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("Async error in method={}, params={}", method.getName(), Arrays.toString(params), ex);
    }
}
```

**좋은 이유:**

- async executor와 scheduler를 분리
- thread prefix로 운영 추적 가능
- void @Async 예외를 방치하지 않음

## 나쁜 예시

### 예시 1. 한 메서드에 스케줄/비동기/재시도를 다 겹친다

```java
@Service
public class BadCleanupService {

    @Scheduled(fixedRate = 1000)
    @Async
    @Retryable
    public void run() {
        // 핵심 업무 + 외부 호출 + 후속 처리까지 한곳에 몰아넣음
    }
}
```

**나쁜 이유:**

- 실행 경계가 불명확함
- 실패 전파/관측/재시도 범위가 애매함
- 어떤 책임 때문에 실패했는지 읽기 어려움
- 기본 retry 정책에 의존하기 쉬움

### 예시 2. self-invocation으로 @Async / @Retryable 효과를 기대한다

```java
@Service
@RequiredArgsConstructor
public class BadNotificationService {

    public void sendAll(List<String> ids) {
        for (String id : ids) {
            this.sendOne(id); // 프록시를 거치지 않음
        }
    }

    @Async
    public void sendOne(String id) {
        // ...
    }
}
```

**나쁜 이유:**

- 같은 클래스 내부 호출이라 프록시 적용을 기대하면 안 됨

### 예시 3. retry를 결정적 실패에 건다

```java
@Component
public class BadMapper {

    @Retryable(maxAttempts = 5)
    public UserId map(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("raw must not be blank");
        }
        return new UserId(raw);
    }
}
```

**나쁜 이유:**

- 입력 검증 실패는 재시도로 해결되지 않음
- retry 대상 예외를 좁히지 않음
- domain/value 생성 로직에 retry를 붙임
