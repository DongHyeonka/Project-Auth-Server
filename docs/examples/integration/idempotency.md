# Integration Idempotency 예시

## 좋은 예시

### 예시 1. provider 공식 header를 adapter에서 설정한다

```java
@Service
@RequiredArgsConstructor
class StripePaymentClient {

    private final RestClient restClient;

    ChargeResult createCharge(CreateChargeCommand command) {
        return restClient.post()
                .uri("/v1/payment_intents")
                .header("Idempotency-Key", command.providerIdempotencyKey())
                .body(StripeCreateChargeRequest.from(command))
                .retrieve()
                .body(ChargeResult.class);
    }
}
```

**좋은 이유:**

- provider 공식 idempotency header를 adapter 경계에서 설정한다
- application/domain이 HTTP 헤더 이름을 몰라도 된다
- 같은 command 재전송 시 같은 key를 쓸 수 있다.

### 예시 2. 내부 command id와 provider key를 분리해 관리한다

```java
public record OutboundCallKey(
        String outboundCommandId,
        String provider,
        String operation,
        String providerIdempotencyKey
) {
}

public record OutboundFingerprint(
        String requestDigest
) {
}
```

**좋은 이유:**

- 내부 추적 키와 provider 전송 키가 분리된다
- provider별 operation scope 차이를 표현하기 쉽다
- fingerprint 충돌 검사를 붙이기 좋다

### 예시 3. timeout 후 같은 key로 재전송한다

```java
try {
    return paypalCaptureClient.capture(command);
} catch (ExternalTimeoutException ex) {
    return paypalCaptureClient.capture(command.withSameProviderIdempotencyKey());
}
```

**좋은 이유:**

- side effect 재시도 시 새 key를 만들지 않는다
- 같은 요청 의도에 같은 provider key를 재사용한다
- PayPal/Stripe 문서 취지와 맞는다.

### 예시 4. 같은 key 동시 송신을 막는다

```java
if (!outboundIdempotencyCoordinator.tryAcquire(command.provider(), command.operation(), command.providerIdempotencyKey())) {
    throw new DuplicateOutboundCallInProgressException();
}
```

**좋은 이유:**

- 같은 key 두 번 동시 전송을 줄인다
- PayPal이 설명한 concurrent duplicate 문제를 완화할 수 있다.

## 나쁜 예시

### 예시 1. timeout 후 새 key로 다시 보낸다

```java
try {
    return stripeClient.createCharge(command.withNewProviderIdempotencyKey());
} catch (ExternalTimeoutException ex) {
    return stripeClient.createCharge(command.withNewProviderIdempotencyKey());
}
```

**나쁜 이유:**

- 같은 외부 side effect 요청이 새 요청으로 처리될 수 있다
- 중복 생성/중복 결제 위험이 커진다

### 예시 2. 같은 key를 다른 operation에 재사용한다

```java
String key = "7f6d...";
authorizePayment(key);
capturePayment(key);
```

**나쁜 이유:**

- provider마다 operation scope가 다를 수 있다
- PayPal은 API call type 단위 고유성을 요구한다.

### 예시 3. provider key에 이메일을 넣는다

```java
String providerIdempotencyKey = request.email() + ":" + request.orderId();
```

**나쁜 이유:**

- PII가 key에 섞인다
- Stripe도 민감정보를 key로 쓰지 말라고 권고한다.

### 예시 4. replay semantics를 무시하고 항상 “새 성공”으로 해석한다

```java
return new PaymentCapturedResult(true, true);
```

**나쁜 이유:**

- provider가 이전 결과 재생인지 최신 상태 조회인지 구분하지 못한다
- 내부 감사/운영 추적이 부정확해진다
