# Fallback 예시

## 좋은 예시

### 예시 1. 외부 추천 실패 시 빈 추천 목록으로 degrade한다

```java
@Service
@RequiredArgsConstructor
public class RecommendationIntegrationService {

    private final RecommendationClient recommendationClient;

    public RecommendationResult getRecommendations(String userId) {
        try {
            return recommendationClient.getRecommendations(userId);
        } catch (ExternalRecommendationTemporaryFailure ex) {
            return RecommendationResult.degradedEmpty();
        }
    }
}
```

**좋은 이유:**

- 추천은 soft dependency로 다룰 수 있다
- 핵심 기능을 깨지 않고 degraded mode를 제공한다
- fallback 위치가 integration 경계에 있다

### 예시 2. 캐시된 공개키로 fallback한다

```java
@Service
@RequiredArgsConstructor
public class JwkIntegrationService {

    private final JwkClient jwkClient;
    private final JwkCache jwkCache;

    public JwkSetResult getJwkSet() {
        try {
            JwkSetResult result = jwkClient.fetch();
            jwkCache.put(result);
            return result;
        } catch (ExternalJwkTemporaryFailure ex) {
            return jwkCache.get()
                    .orElseThrow(() -> ex);
        }
    }
}
```

**좋은 이유:**

- 조회성 데이터에 짧은 TTL 캐시 fallback을 적용할 수 있다
- fallback 가능성과 불가능성이 함께 표현된다
- 외부 실패를 무조건 숨기지 않는다

### 예시 3. CircuitBreaker fallback을 명시적으로 둔다

```java
@Service
@RequiredArgsConstructor
public class ExternalProfileService {

    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final ExternalProfileClient externalProfileClient;

    public ProfileSupplementResult getSupplement(String userId) {
        return circuitBreakerFactory.create("external-profile")
                .run(
                        () -> externalProfileClient.getProfile(userId),
                        throwable -> ProfileSupplementResult.degradedUnavailable()
                );
    }
}
```

**좋은 이유:**

- Spring Cloud CircuitBreaker의 공식 fallback 모델을 따른다
- fallback 결과가 별도 degraded result로 표현된다.

### 예시 4. 이메일 발송은 비동기 접수로 degrade할 수 있다

```java
@Service
@RequiredArgsConstructor
public class MailIntegrationService {

    private final MailClient mailClient;
    private final MailOutboxRepository mailOutboxRepository;

    public MailDispatchResult sendVerificationMail(MailCommand command) {
        try {
            mailClient.send(command);
            return MailDispatchResult.sent();
        } catch (ExternalMailTemporaryFailure ex) {
            mailOutboxRepository.enqueue(command);
            return MailDispatchResult.acceptedForRetry();
        }
    }
}
```

**좋은 이유:**

- 즉시 발송 실패를 비동기 재처리로 전환한다
- API 의미를 “즉시 완료”가 아니라 “접수됨”으로 명확히 바꿀 수 있다
- hard dependency를 soft dependency로 바꾸는 사례다.

## 나쁜 예시

### 예시 1. 결제 확정 실패를 성공처럼 fallback한다

```java
public PaymentCaptureResult capture(CaptureCommand command) {
    try {
        return paymentClient.capture(command);
    } catch (Exception ex) {
        return PaymentCaptureResult.success();
    }
}
```

**나쁜 이유:**

- 실제 결제 확정 실패를 성공처럼 숨긴다
- 정합성과 감사 가능성을 깨뜨린다
- fallback을 쓰면 안 되는 대표 사례다

### 예시 2. 오래된 캐시를 무기한 사용한다

```java
public ExchangeRateResult getRate(String currency) {
    try {
        return exchangeRateClient.getRate(currency);
    } catch (Exception ex) {
        return foreverCache.get(currency);
    }
}
```

**나쁜 이유:**

- stale budget이 없다
- 오래된 데이터를 최신 사실처럼 쓰게 된다
- 운영에서 품질 저하를 통제할 수 없다

### 예시 3. controller에서 fallback을 직접 구현한다

```java
@RestController
@RequiredArgsConstructor
public class UserController {

    private final ExternalProfileClient externalProfileClient;

    @GetMapping("/api/v1/users/{userId}")
    public ApiResult<UserResponse> get(@PathVariable String userId) {
        try {
            ExternalProfileResponse response = externalProfileClient.getProfile(userId);
            return ApiResult.success(UserResponse.from(response));
        } catch (Exception ex) {
            return ApiResult.success(UserResponse.withoutProfile());
        }
    }
}
```

**나쁜 이유:**

- fallback이 controller로 새어 나갔다
- provider-aware 로직이 presentation 경계에 있다
- 공통 observability와 정책 일관성이 깨진다

### 예시 4. fallback 발생을 전혀 기록하지 않는다

```java
try {
    return recommendationClient.getRecommendations(userId);
} catch (Exception ex) {
    return RecommendationResult.degradedEmpty();
}
```

**나쁜 이유:**

- degraded mode가 운영에서 보이지 않는다
- fallback rate를 추적할 수 없다
- upstream 장애가 숨어 버린다
