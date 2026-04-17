# @SpringBootTest 사용 예시

## 좋은 예시

### 예시 1. 애플리케이션 기동 smoke test는 NONE으로 충분하다

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationContextSmokeTest {

    @Test
    void contextLoads() {
    }
}
```

**좋은 이유:**

- 실제 서버가 필요 없는 full-context 기동 확인이다
- Boot 방식으로 애플리케이션이 정상 조립되는지 확인한다
- non-web full-context 테스트에 가장 좁은 환경을 선택했다

Spring Boot는 `@SpringBootTest`가 `SpringApplication`으로 컨텍스트를 만들고, `NONE`은 웹 환경 없이 `ApplicationContext`만 로드한다고 설명한다.

### 예시 2. 여러 레이어가 함께 필요한 서비스 통합 테스트는 NONE을 사용한다

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderCommandServiceIntegrationTest {

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void confirmOrder_changesState() {
        // given

        // when
        orderCommandService.confirm(1L);

        // then
        assertThat(orderRepository.findById(1L)).isPresent();
    }
}
```

**좋은 이유:**

- 서비스, 리포지토리, 트랜잭션, 설정 조합까지 함께 검증한다
- 실제 웹 서버는 필요 없으므로 `NONE`으로 범위를 제한했다
- full context가 필요한 이유가 분명하다

`@SpringBootTest`는 Boot features가 필요한 full application context 테스트에 적합하고, `NONE`은 웹 환경을 만들지 않는다고 Boot 문서가 설명한다.

### 예시 3. 실제 서버는 필요 없지만 MVC/보안/직렬화까지 함께 보고 싶으면 MOCK + MockMvc를 쓴다

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getUser_returns200() throws Exception {
        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk());
    }
}
```

**좋은 이유:**

- 전체 컨텍스트는 유지하면서 실제 내장 서버는 띄우지 않는다
- MVC 설정, 보안 필터, Jackson, 예외 처리 등을 함께 볼 수 있다
- mock 기반 웹 통합 테스트 목적과 잘 맞는다

Spring Boot는 `MOCK`이 내장 서버를 시작하지 않는 mock web environment이고, `@AutoConfigureMockMvc`와 함께 사용할 수 있다고 설명한다.

### 예시 4. 실제 HTTP round-trip이 필요하면 RANDOM_PORT를 사용한다

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserHttpIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getUser_returns200() {
        var response = restTemplate.getForEntity("/users/1", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
```

**좋은 이유:**

- 실제 내장 서버와 실제 HTTP 경로를 검증한다
- 고정 포트 충돌 없이 자동화 테스트에 적합하다
- mock 환경으로는 검증하기 어려운 실제 서버 동작을 본다

Spring Boot는 `RANDOM_PORT`가 실제 `WebServerApplicationContext`를 만들고 임의 포트에 서버를 시작한다고 설명한다.

### 예시 5. full context가 필요하지만 테스트 편의 기능도 원하면 @AutoConfigure…를 조합한다

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserAdminFlowTest {
    // full context + MockMvc 편의 빈 사용
}
```

**좋은 이유:**

- slice로 자를 수는 없지만 테스트 편의 빈은 활용한다
- `@SpringBootTest`와 `@AutoConfigure…`의 역할이 분명하다
- Boot가 공식적으로 허용한 조합이다

Spring Boot는 `@AutoConfigure…` 계열을 `@SpringBootTest`와 함께 사용할 수 있다고 설명한다.

## 나쁜 예시

### 예시 1. 순수 단위 테스트에 @SpringBootTest를 붙인다

```java
@SpringBootTest
class MoneyCalculatorTest {

    @Test
    void add() {
        MoneyCalculator calculator = new MoneyCalculator();
        assertThat(calculator.add(1, 2)).isEqualTo(3);
    }
}
```

**나쁜 이유:**

- Spring 컨테이너가 전혀 필요 없다
- full context 로딩 비용만 추가한다
- 이런 테스트는 순수 JUnit 단위 테스트가 맞다

Spring 문서는 IoC 덕분에 단위 테스트와 통합 테스트를 구분해서 설계할 수 있다고 설명하고, Boot는 `@SpringBootTest`를 Boot features가 필요할 때 쓰는 어노테이션으로 설명한다.

### 예시 2. repository만 검증하려고 @SpringBootTest를 기본값으로 쓴다

```java
@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail() {
        assertThat(userRepository.findByEmail("a@test.com")).isPresent();
    }
}
```

**나쁜 이유:**

- JPA repository만 볼 테스트에 전체 애플리케이션을 띄운다
- 데이터 접근 slice로 충분한 범위를 과도하게 넓힌다
- 테스트 목적 대비 로딩 비용이 크다

Spring Boot는 `@DataJpaTest` 같은 데이터 slice를 별도로 제공한다고 설명한다.

### 예시 3. MVC 계층 검증인데 full context를 기본값으로 쓴다

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {
    // request mapping, validation, status code만 검증
}
```

**나쁜 이유:**

- 테스트 관심사가 MVC 계층에 머무르면 `@WebMvcTest`가 더 정확하다
- full context를 기본값으로 잡으면 테스트 범위와 책임이 흐려진다
- slice로 충분한 대상을 과도하게 넓힌다

Spring Boot는 Spring MVC controller 테스트에 `@WebMvcTest`를 제공한다고 설명한다.

### 예시 4. RANDOM_PORT 테스트에서 @Transactional이면 서버 변경도 롤백된다고 기대한다

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class UserHttpRollbackTest {

    @Test
    void createUser() {
        // HTTP 호출 후 테스트 종료되면 DB도 원복될 것이라고 기대
    }
}
```

**나쁜 이유:**

- 실제 서버와 테스트 메서드는 별도 스레드/별도 트랜잭션이다
- 테스트 메서드 롤백이 서버 쪽 트랜잭션에는 적용되지 않는다
- 데이터 정리 전략을 별도로 가져가야 한다

Spring Boot는 `RANDOM_PORT`/`DEFINED_PORT`에서 서버와 클라이언트가 별도 스레드에서 실행되므로 서버 쪽 트랜잭션은 테스트 롤백으로 되돌아가지 않는다고 명시한다.

### 예시 5. 여러 slice annotation을 동시에 섞는다

```java
@WebMvcTest
@DataJpaTest
class MixedSliceTest {
}
```

**나쁜 이유:**

- Spring Boot가 지원하지 않는 조합이다
- 테스트 범위가 애매하고 자동 구성도 예측하기 어렵다
- slice가 여러 개 필요하면 하나를 고르고 나머지는 수동으로 추가해야 한다

Spring Boot는 여러 `@…Test` slice annotation을 한 테스트에 함께 사용하는 것은 지원하지 않는다고 설명한다.

### 예시 6. 작은 차이마다 다른 @SpringBootTest 구성을 만들어 컨텍스트 캐시를 깨뜨린다

```java
@SpringBootTest(properties = "feature.a=true")
class ATest {
}

@SpringBootTest(properties = "feature.a=false")
class BTest {
}

@SpringBootTest(properties = "feature.b=true")
class CTest {
}
```

**나쁜 이유:**

- 목적이 비슷한 테스트인데 서로 다른 컨텍스트를 계속 만든다
- static context cache 재사용이 줄어든다
- 전체 테스트 시간이 불필요하게 늘 수 있다

Spring TestContext Framework는 `ApplicationContext`를 static cache에 저장해 재사용한다고 설명한다. 컨텍스트 구성이 달라질수록 재사용 이점이 줄어든다.
