# @ConfigurationProperties 예시

## 좋은 예시 1: 의미 있는 설정 집합을 타입으로 묶음

```java
@ConfigurationProperties(prefix = "auth.jwt")
@Validated
public record JwtProperties(
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank String issuer
) {}
```

**왜 좋은가:**

- 관련 설정이 하나의 계약으로 묶인다
- 타입 안전성과 검증이 있다
- scattered @Value를 줄인다

## 좋은 예시 2: configuration properties scanning 사용

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthApplication {
}
```

**왜 좋은가:**

- 애플리케이션 내부 properties 타입을 명시적으로 스캔한다
- @Component에 기대지 않는다

## 좋은 예시 3: 조건부/명시 등록은 EnableConfigurationProperties

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VaultProperties.class)
public class VaultConfiguration {
}
```

**왜 좋은가:**

- 어떤 설정 타입을 활성화하는지 명확하다
- auto-configuration/조건부 wiring에 잘 맞는다

## 좋은 예시 4: third-party bean에 바인딩

```java
@Configuration(proxyBeanMethods = false)
public class ClientConfiguration {

    @Bean
    @ConfigurationProperties("app.http.client")
    public HttpClientProperties httpClientProperties() {
        return new HttpClientProperties();
    }
}
```

**왜 좋은가:**

- 외부 타입/서드파티 설정을 명시적 config 안에 가둔다
- prefix와 등록 위치가 분명하다

## 나쁜 예시 1: 산발적 @Value 남발

```java
@Service
public class JwtIssuer {

    @Value("${auth.jwt.access-token-ttl}")
    private Duration accessTokenTtl;

    @Value("${auth.jwt.refresh-token-ttl}")
    private Duration refreshTokenTtl;

    @Value("${auth.jwt.issuer}")
    private String issuer;
}
```

**문제:**

- 관련 설정이 흩어진다
- 타입 집합과 검증이 약해진다
- 재사용/문서화가 어려워진다

**개선:**

- JwtProperties로 묶어서 주입

## 나쁜 예시 2: Optional 필드 사용

```java
@ConfigurationProperties("vault")
public record VaultProperties(
        Optional<String> namespace
) {}
```

**문제:**

- Spring Boot 공식 문서가 권장하지 않는다
- 값이 없으면 empty Optional이 아니라 null이 바인딩될 수 있다

**개선:**

- nullable String
- 명시적 기본값
- 별도 default 처리

## 나쁜 예시 3: properties class에 business logic 포함

```java
@ConfigurationProperties("auth.jwt")
public class JwtProperties {

    private Duration accessTokenTtl;

    public String issueToken(User user) {
        ...
    }
}
```

**문제:**

- 설정 계약과 비즈니스 로직이 섞인다
- 테스트/책임 분리가 흐려진다

## 나쁜 예시 4: CommonProperties dump zone

```java
@ConfigurationProperties("app")
public class AppProperties {
    private String jwtIssuer;
    private Duration retryDelay;
    private String vaultAddress;
    private String mailFrom;
}
```

**문제:**

- 소유 기능이 다 다르다
- prefix와 책임이 너무 넓다
- 기능별 변경이 서로 얽힌다
