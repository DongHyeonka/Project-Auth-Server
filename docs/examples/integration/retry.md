# Retry 예시

## 좋은 예시

### 예시 1. retry 대상 예외와 backoff를 명시한다

```java
@Component
@RequiredArgsConstructor
public class ExternalTokenGateway {

    private final ExternalTokenClient externalTokenClient;

    @Retryable(
            retryFor = {
                    SocketTimeoutException.class,
                    ConnectException.class,
                    ResourceAccessException.class
            },
            noRetryFor = {
                    IllegalArgumentException.class,
                    ExternalAuthenticationRejectedException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 300, maxDelay = 2_000, multiplier = 2.0, random = true)
    )
    public TokenResult issueToken(TokenCommand command) {
        return externalTokenClient.issueToken(command);
    }
}
```

**좋은 이유:**

- retry 대상을 좁혔다
- business rejection은 제외했다
- backoff + jitter 성격(random = true)을 명시했다.

### 예시 2. 최종 실패만 내부 예외로 번역한다

```java
@Component
@RequiredArgsConstructor
public class KeycloakTokenClient {

    private final RestClient restClient;

    public TokenResult issue(TokenCommand command) {
        try {
            return doIssue(command);
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            throw new ExternalAuthTemporaryFailureException(ex);
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new ExternalAuthRejectedException(ex);
        }
    }

    private TokenResult doIssue(TokenCommand command) {
        return restClient.post()
                .uri("/protocol/openid-connect/token")
                .body(command)
                .retrieve()
                .body(TokenResult.class);
    }
}
```

**좋은 이유:**

- provider-specific HTTP 오류를 내부 의미로 번역한다
- application이 raw HTTP client 예외를 직접 보지 않는다

### 예시 3. retry 후 성공은 WARN으로 남긴다

```java
log.warn("External auth request succeeded after retry. provider={} operation={} attempts={}",
        "keycloak", "issue-token", attemptCount);
```

**좋은 이유:**

- 중간 장애 징후를 추적 가능하게 남긴다
- 최종 성공을 장애처럼 ERROR로 과장하지 않는다

### 예시 4. provider rate limit 신호를 존중한다

```java
if (response.getStatusCode().value() == 429) {
    Duration retryAfter = parseRetryAfter(response.getHeaders());
    throw new RetryableRateLimitedException(retryAfter);
}
```

**좋은 이유:**

- provider가 주는 throttling 신호를 반영할 수 있다
- 무작정 같은 간격으로 재시도하지 않는다

## 나쁜 예시

### 예시 1. 모든 예외를 그대로 retry한다

```java
@Retryable
public void callExternalApi() {
    // ...
}
```

**나쁜 이유:**

- Spring 기본값은 모든 예외를 재시도할 수 있다
- deterministic failure와 business rejection까지 재시도될 수 있다.

### 예시 2. backoff 없이 즉시 재시도한다

```java
for (int i = 0; i < 3; i++) {
    try {
        return call();
    } catch (Exception ignored) {
    }
}
```

**나쁜 이유:**

- retry without backoff anti-pattern이다
- 순간 장애 시 부하를 더 키운다.

### 예시 3. side effect API를 idempotency 검토 없이 다시 호출한다

```java
try {
    paymentClient.capture(request);
} catch (TimeoutException ex) {
    paymentClient.capture(request);
}
```

**나쁜 이유:**

- timeout은 side effect 미발생을 보장하지 않는다
- non-idempotent retry anti-pattern에 가깝다.

### 예시 4. SDK retry와 adapter retry를 동시에 켠다

```java
public void send() {
    sdkClient.send(); // SDK 내부 retry 있음
}
```

그리고 바깥에서 다시 @Retryable 적용

**나쁜 이유:**

- retry layering anti-pattern이다
- 실제 요청 수와 부하가 폭증할 수 있다.
