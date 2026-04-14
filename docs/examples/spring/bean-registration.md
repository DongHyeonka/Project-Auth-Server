# bean registration 예시

## 좋은 예시 1: application service는 stereotype 등록

```java
@Service
public class RegisterUserService implements RegisterUserUseCase {
    ...
}
```

**왜 좋은가:**

- 애플리케이션 주 컴포넌트라는 역할이 드러난다
- scanning 기반 등록에 자연스럽다

## 좋은 예시 2: external client는 configuration + bean

```java
@Configuration
public class VaultClientConfiguration {

    @Bean
    public VaultTransitClient vaultTransitClient(
            VaultProperties properties,
            ObjectMapper objectMapper
    ) {
        return new VaultTransitClient(
                properties.address(),
                properties.token(),
                HttpClient.newHttpClient(),
                objectMapper
        );
    }
}
```

**왜 좋은가:**

- 외부 라이브러리/인프라 객체 조립이 한 곳에 모인다
- 생성 로직이 명시적이다

## 좋은 예시 3: security/filter wiring은 configuration에 둠

```java
@Configuration
public class WebConfiguration {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        return registration;
    }
}
```

**왜 좋은가:**

- framework wiring 성격이 분명하다
- business component와 분리된다

## 좋은 예시 4: domain object는 bean으로 등록하지 않음

```java
public record UserEmail(String value) {}
```

**왜 좋은가:**

- value object는 container 관리 대상이 아니다
- 생성/검증 책임은 domain에 남는다

## 나쁜 예시 1: domain entity를 bean으로 등록

```java
@Component
public class User {
    ...
}
```

**문제:**

- domain object 생명주기를 container가 소유하게 된다
- 의미가 맞지 않는다

## 나쁜 예시 2: @Component 안에 습관적 @Bean

```java
@Component
public class UserFactoryComponent {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**문제:**

- full @Configuration 대신 lite mode가 된다
- configuration 역할과 component 역할이 섞인다

**개선:**

- 별도 @Configuration 클래스로 이동

## 나쁜 예시 3: 의미 없는 잡다한 config

```java
@Configuration
public class CommonConfig {
    @Bean ...
    @Bean ...
    @Bean ...
}
```

**문제:**

- 어떤 조립을 담당하는지 이름만 보고 알기 어렵다
- 변경 이유가 다른 bean이 섞이기 쉽다

## 나쁜 예시 4: 단순 helper까지 bean으로 올림

```java
@Component
public class StringMaskingHelper {
    public String mask(String input) { ... }
}
```

**문제:**

- lifecycle/DI 이득이 작다
- plain helper로 둘 수 있다면 굳이 bean일 필요가 없다
