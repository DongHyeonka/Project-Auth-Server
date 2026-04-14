# Timeout 예시

## 좋은 예시

### 예시 1. 전역 기본값은 공통 설정으로 둔다

```yaml
spring:
  http:
    clients:
      connect-timeout: 500ms
      read-timeout: 2s
```

**좋은 이유:**

- 서비스 전체 기본값이 한 곳에 있다
- 모든 client에 최소 timeout 정책이 적용된다.

### 예시 2. provider별 차이는 HTTP service group에서 override한다

```yaml
spring:
  http:
    clients:
      connect-timeout: 500ms
    serviceclient:
      keycloak:
        base-url: https://keycloak.example.com
        read-timeout: 3s
      payment:
        base-url: https://payment.example.com
        read-timeout: 5s
```

**좋은 이유:**

- 공통 기본값과 provider별 차이가 함께 보인다
- Spring Boot가 제공하는 group-level connect/read timeout 구조와 맞는다.

### 예시 3. RestClient는 주입된 builder를 사용한다

```java
@Service
class KeycloakTokenClient {

    private final RestClient restClient;

    KeycloakTokenClient(RestClient.Builder builder, KeycloakProperties properties) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .build();
    }
}
```

**좋은 이유:**

- Boot auto-configuration과 공통 timeout/customizer를 따른다
- RestClient.create()로 공통 구성을 우회하지 않는다.

### 예시 4. WebClient는 Reactor Netty timeout을 명시적으로 구성할 수 있다

```java
@Bean
WebClient paymentWebClient(WebClient.Builder builder) {
    HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(3))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);

    return builder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl("https://payment.example.com")
            .build();
}
```

**좋은 이유:**

- connect timeout과 response timeout을 분리한다
- Reactor Netty의 구체 timeout 지점을 활용한다.

### 예시 5. timeout 값은 operation별로 명시적 override만 허용한다

```java
Mono<ResponseDto> callLongRunningOperation(RequestDto request) {
    return webClient.post()
            .uri("/reports")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ResponseDto.class)
            .timeout(Duration.ofSeconds(8));
}
```

**좋은 이유:**

- “이 operation만 더 길다”는 의도가 코드에 드러난다
- 기본값과 다른 이유를 문서화하기 쉽다

**주의:**

- reactive 전체 timeout()은 최후 수단에 가깝고, 가능하면 client-specific timeout이 더 우선이다.

## 나쁜 예시

### 예시 1. 외부 호출에 timeout이 없다

```java
@Service
class BadExternalClient {

    private final RestClient restClient = RestClient.create("https://example.com");
}
```

**나쁜 이유:**

- 공통 timeout/customizer/관측 구성을 우회한다
- 무제한 또는 의도 불명확한 대기에 빠질 수 있다.

### 예시 2. 너무 낮은 timeout을 근거 없이 하드코딩한다

```java
webClient.get()
        .uri("/token")
        .retrieve()
        .bodyToMono(TokenResponse.class)
        .timeout(Duration.ofMillis(20));
```

**나쁜 이유:**

- TLS handshake, 새 연결, DNS 비용을 고려하지 않은 값일 수 있다
- 배포 직후/콜드 커넥션에서 false timeout을 유발하기 쉽다.

### 예시 3. timeout 값을 서비스 전체에 하나의 숫자로 강제한다

```yaml
external:
  timeout-ms: 1000
```

**나쁜 이유:**

- provider별 latency와 business 중요도가 다를 수 있다
- connect/read/response 구분도 사라진다
- operation별 차이를 담기 어렵다

### 예시 4. timeout 이후 side effect API를 무심코 재시도한다

```java
try {
    paymentClient.capture(request);
} catch (TimeoutException ex) {
    paymentClient.capture(request);
}
```

**나쁜 이유:**

- timeout이 side effect 미발생을 보장하지 않는다
- idempotency 검토 없이 중복 실행 위험이 생긴다.
