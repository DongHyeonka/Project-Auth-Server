# Integration Serialization / Deserialization 예시

## 좋은 예시

### 예시 1. 외부 response DTO만 관대하게 읽는다

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUserResponse(
        @JsonProperty("id") String id,
        @JsonProperty("email") String email,
        @JsonProperty("enabled") boolean enabled
) {
}
```

**좋은 이유:**

- provider가 필드를 추가해도 파싱이 덜 깨진다
- 외부 필드명 mismatch를 DTO 경계에서 해결한다
- 내부 모델로 바로 새지 않는다.

### 예시 2. 성공 응답과 오류 응답 DTO를 분리한다

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeChargeResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeErrorResponse(
        @JsonProperty("error") StripeErrorBody error
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeErrorBody(
        @JsonProperty("type") String type,
        @JsonProperty("code") String code,
        @JsonProperty("message") String message
) {
}
```

**좋은 이유:**

- success/error shape를 억지로 하나의 DTO에 우겨 넣지 않는다
- adapter가 provider failure semantics를 더 명확하게 번역할 수 있다

### 예시 3. form-urlencoded 계약은 JSON으로 억지 변환하지 않는다

```java
@Service
@RequiredArgsConstructor
class KeycloakTokenClient {

    private final RestClient restClient;

    TokenResult issue(KeycloakTokenCommand command) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", command.clientId());
        form.add("username", command.username());
        form.add("password", command.password());

        KeycloakTokenResponse response = restClient.post()
                .uri("/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KeycloakTokenResponse.class);

        return new TokenResult(response.accessToken(), response.expiresIn());
    }
}
```

**좋은 이유:**

- provider media type을 정확히 따른다
- form 계약을 JSON DTO로 왜곡하지 않는다
- Spring converter 지원과도 맞는다.

### 예시 4. provider-specific weird format은 adapter mapper에서 흡수한다

```java
public record ExternalPaymentResult(
        String paymentId,
        PaymentState state
) {
}

public class StripePaymentMapper {

    ExternalPaymentResult toResult(StripeChargeResponse response) {
        return new ExternalPaymentResult(
                response.id(),
                switch (response.status()) {
                    case "succeeded" -> PaymentState.SUCCEEDED;
                    case "processing" -> PaymentState.PROCESSING;
                    default -> PaymentState.UNKNOWN;
                }
        );
    }
}
```

**좋은 이유:**

- provider string enum이 domain enum으로 직접 새지 않는다
- 새 값이 추가돼도 UNKNOWN으로 흡수할 수 있다

### 예시 5. 공통 builder를 주입받아 client를 만든다

```java
@Service
class ExternalUserClient {

    private final RestClient restClient;

    ExternalUserClient(RestClient.Builder builder, ExternalUserProperties properties) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .build();
    }
}
```

**좋은 이유:**

- 공통 HttpMessageConverters와 request factory를 따른다
- 로컬 ObjectMapper/client 생성을 줄인다.

## 나쁜 예시

### 예시 1. 내부 entity를 외부 request body로 직접 보낸다

```java
@Entity
public class User {
    @Id
    private Long id;
    private String email;
    private String password;
    private String role;
}

restClient.post()
        .uri("/users")
        .body(user)
        .retrieve();
```

**나쁜 이유:**

- 내부 모델이 외부 계약으로 새어 나간다
- provider에 보내면 안 되는 필드까지 함께 나갈 수 있다
- serialization concern이 domain/entity를 오염시킨다

### 예시 2. adapter 메서드 안에서 new ObjectMapper()를 만든다

```java
public ExternalUserResult getUser(String id) throws Exception {
    String body = httpClient.get(...);
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(body, ExternalUserResult.class);
}
```

**나쁜 이유:**

- 공통 mapper/configuration을 우회한다
- client별 일관성이 깨진다
- message converter 경계를 스스로 무너뜨린다.

### 예시 3. external response를 raw Map으로 받아 business 로직에 넘긴다

```java
Map<String, Object> response = restClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .body(Map.class);

return userService.handle(response);
```

**나쁜 이유:**

- payload reading 경계가 application으로 번진다
- contract drift가 여러 계층에 퍼진다
- tolerant reader가 아니라 “아무도 책임지지 않는 reader”가 된다

### 예시 4. 외부 오류 본문을 그대로 예외 메시지로 올린다

```java
catch (HttpClientErrorException ex) {
    throw new RuntimeException(ex.getResponseBodyAsString());
}
```

**나쁜 이유:**

- provider raw payload가 내부 예외/로그로 새어 나간다
- 민감정보나 과도한 본문이 포함될 수 있다
- success/error parsing 규칙이 사라진다
