# External API Client Structure 예시

## 좋은 예시

### 예시 1. imperative 서비스에서 RestClient adapter를 infrastructure에 둔다

```java
public interface ExternalTokenPort {
    ExternalTokenResult issueToken(ExternalTokenCommand command);
}

@Service
@RequiredArgsConstructor
class KeycloakTokenClient implements ExternalTokenPort {

    private final RestClient restClient;
    private final KeycloakTokenMapper keycloakTokenMapper;

    KeycloakTokenClient(RestClient.Builder restClientBuilder,
                        KeycloakProperties properties,
                        KeycloakAuthHeaderCustomizer authHeaderCustomizer) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", "project-auth-server")
                .requestInterceptor(authHeaderCustomizer)
                .build();
        this.keycloakTokenMapper = new KeycloakTokenMapper();
    }

    @Override
    public ExternalTokenResult issueToken(ExternalTokenCommand command) {
        KeycloakTokenRequest request = keycloakTokenMapper.toRequest(command);

        KeycloakTokenResponse response = restClient.post()
                .uri("/protocol/openid-connect/token")
                .body(request)
                .retrieve()
                .body(KeycloakTokenResponse.class);

        return keycloakTokenMapper.toResult(response);
    }
}
```

**좋은 이유:**

- 외부 호출이 infrastructure adapter에 있다
- RestClient.Builder를 주입받아 공통 구성과 관측을 따른다
- 외부 DTO와 내부 결과가 분리된다.

### 예시 2. reactive 경계에서는 WebClient를 사용한다

```java
@Service
class ExternalAuditClient {

    private final WebClient webClient;

    ExternalAuditClient(WebClient.Builder webClientBuilder, AuditProperties properties) {
        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
    }

    Mono<Void> send(AuditEventRequest request) {
        return webClient.post()
                .uri("/events")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
```

**좋은 이유:**

- non-blocking 흐름에 맞는 client를 쓴다
- Boot가 권장하는 WebClient.Builder 주입 방식을 따른다.

### 예시 3. HTTP Service Client를 group 기반으로 묶는다

```java
@HttpExchange
public interface KeycloakUserHttpClient {

    @GetExchange("/admin/realms/{realm}/users/{id}")
    KeycloakUserResponse getUser(@PathVariable String realm, @PathVariable String id);
}

@ImportHttpServices(group = "keycloak", types = KeycloakUserHttpClient.class)
@Configuration
class KeycloakHttpClientsConfiguration {
}
```

**좋은 이유:**

- 선언형 인터페이스로 계약이 분명하다
- group을 통해 URL, timeout, SSL, auth customization을 함께 묶을 수 있다.

### 예시 4. 외부 DTO와 내부 결과를 명시적으로 분리한다

```java
public record KeycloakUserResponse(
        @JsonProperty("id") String id,
        @JsonProperty("email") String email,
        @JsonProperty("enabled") boolean enabled
) {
}

public record ExternalUserResult(
        String externalUserId,
        String email,
        boolean active
) {
}

public class KeycloakUserMapper {

    ExternalUserResult toResult(KeycloakUserResponse response) {
        return new ExternalUserResult(
                response.id(),
                response.email(),
                response.enabled()
        );
    }
}
```

**좋은 이유:**

- provider JSON 계약이 내부 모델로 그대로 번지지 않는다
- 필드명 mismatch와 provider 의미를 adapter 경계에 가둔다

## 나쁜 예시

### 예시 1. controller가 외부 API를 직접 호출한다

```java
@RestController
@RequiredArgsConstructor
class BadTokenController {

    private final RestClient.Builder restClientBuilder;

    @PostMapping("/api/v1/tokens")
    ApiResult<?> create(@RequestBody CreateTokenRequest request) {
        KeycloakTokenResponse response = restClientBuilder.build()
                .post()
                .uri("https://keycloak.example.com/token")
                .body(request)
                .retrieve()
                .body(KeycloakTokenResponse.class);

        return ApiResult.success(response);
    }
}
```

**나쁜 이유:**

- controller가 외부 연동과 transport 변환을 직접 수행한다
- base URL이 하드코딩돼 있다
- 외부 DTO가 내부 API 응답으로 그대로 노출된다

### 예시 2. 외부 DTO를 application 시그니처에 그대로 넘긴다

```java
@Service
class BadIssueTokenService {

    public void issue(KeycloakTokenRequest request) {
        // ...
    }
}
```

**나쁜 이유:**

- application이 provider 계약에 결합된다
- 외부 필드명/형식 변화가 내부 계층으로 번진다

### 예시 3. RestClient.create()를 직접 써서 공통 구성을 우회한다

```java
@Service
class BadExternalClient {

    private final RestClient client = RestClient.create("https://example.org");
}
```

**나쁜 이유:**

- Boot auto-configuration, customizer, instrumentation 적용을 우회한다.

### 예시 4. provider-specific 예외를 그대로 내부로 던진다

```java
public ExternalUserResult getUser(String id) {
    try {
        return webClient.get()
                .uri("/users/{id}", id)
                .retrieve()
                .bodyToMono(ExternalUserResult.class)
                .block();
    } catch (WebClientResponseException ex) {
        throw ex;
    }
}
```

**나쁜 이유:**

- application이 HTTP status와 client exception 타입에 직접 묶인다
- 예외 번역 책임이 adapter 밖으로 새어 나간다
