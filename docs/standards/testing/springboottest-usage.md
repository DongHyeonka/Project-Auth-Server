# @SpringBootTest 사용 기준

## 1. 목적

이 문서는 Spring Boot 테스트에서 `@SpringBootTest`를 언제 사용하고, 언제 사용하지 말아야 하는지를 정의한다.

이 문서의 목표는 다음과 같다.

- `@SpringBootTest`를 풀 애플리케이션 컨텍스트 통합 테스트 용도로 한정한다
- slice test, repository test, 순수 단위 테스트와 역할을 구분한다
- `webEnvironment`별 의미를 명확히 나눈다
- 느리고 넓은 테스트를 기본값으로 삼지 않도록 기준을 만든다

Spring Boot는 `@SpringBootTest`가 `SpringApplication`을 통해 테스트용 `ApplicationContext`를 만들며, Boot 기능이 필요할 때 표준 `@ContextConfiguration`의 대안으로 사용할 수 있다고 설명한다. 또한 더 좁은 범위를 위한 여러 `@…Test` slice annotation도 함께 제공한다고 설명한다.

## 2. 근거 수준

- Official: Spring Boot / Spring Framework 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 Spring Boot의 Testing Spring Boot Applications, `@SpringBootTest` API 문서, test slices 문서, Spring Framework의 transaction-in-test 및 context caching 문서를 기준으로 작성한다. `@SpringBootTest`의 컨텍스트 로딩 방식, `webEnvironment`, test slice, rollback 주의사항, 컨텍스트 캐시는 모두 공식 문서로 직접 확인 가능하다.

## 3. 기본 원칙

### 3.1 @SpringBootTest는 “전체 애플리케이션 조립이 정말 필요한 테스트”에만 사용한다

`@SpringBootTest`는 Spring Boot 기능이 적용된 실제 애플리케이션 컨텍스트를 띄운다. 따라서 이 어노테이션의 기본 의미는 “빈 하나만 검증”이 아니라 애플리케이션이 Boot 방식으로 조립되었을 때도 기대한 동작이 나오는지 확인하는 것이다. Boot 문서도 `@SpringBootTest`를 Boot features가 필요할 때 사용하는 어노테이션으로 설명하고, 더 구체적인 영역 테스트에는 slice annotation을 사용하라고 안내한다.

### 3.2 더 좁은 범위로 충분하면 @SpringBootTest를 쓰지 않는다

Spring Boot는 `@WebMvcTest`, `@JsonTest`, `@DataJpaTest` 같은 slice 테스트를 제공하고, 이들은 애플리케이션의 특정 부분만 auto-configuration 해 준다고 설명한다. 따라서 controller, JSON 직렬화, JPA repository처럼 대상 범위가 좁다면 먼저 slice test를 검토하고, 정말로 여러 레이어와 Boot auto-configuration을 함께 검증해야 할 때만 `@SpringBootTest`를 올린다.

### 3.3 @SpringBootTest는 느릴 수 있다는 전제를 갖고 쓴다

Spring TestContext Framework는 `ApplicationContext`를 static cache에 저장해 같은 컨텍스트는 재사용할 수 있게 해 주지만, 컨텍스트 로딩 자체는 여전히 비용이 크고, 컨텍스트 종류가 많아질수록 전체 테스트 시간이 늘어날 수 있다. 따라서 `@SpringBootTest`는 “편하니까 기본값”이 아니라, 컨텍스트 로딩 비용을 감수할 가치가 있는 테스트에만 써야 한다.

## 4. 언제 @SpringBootTest를 사용하는가

### 4.1 Boot auto-configuration과 실제 빈 조합을 함께 검증해야 할 때 사용한다

`@SpringBootTest`는 `SpringApplication`으로 컨텍스트를 만들고, 외부 설정, 로깅, Boot 기능을 기본적으로 적용한 상태를 재현한다. 따라서 실제 `@ConfigurationProperties`, auto-configuration, component scanning, AOP, security chain, application event wiring, 실제 빈 조합을 함께 검증해야 할 때 적합하다.

### 4.2 애플리케이션이 정상적으로 기동되는지 확인하는 smoke test에 사용한다

`@SpringBootTest`는 별도 설정이 없으면 `@SpringBootConfiguration`을 자동으로 찾고, Boot 방식으로 컨텍스트를 로딩한다. 따라서 `contextLoads()` 같은 smoke test는 `@SpringBootTest`의 대표적인 사용처다. 이는 “서비스가 실제 배포 구성을 기준으로 부팅 가능한가”를 빠르게 확인하는 최소 통합 테스트다.

### 4.3 여러 레이어를 한 번에 검증하는 use case 통합 테스트에 사용한다

컨트롤러-서비스-리포지토리-트랜잭션-AOP-설정 바인딩이 함께 맞물린 동작을 검증해야 한다면 slice test만으로는 부족할 수 있다. 이런 경우 `@SpringBootTest`가 적합하다. 다만 이때도 “한 서비스 메서드 통합”, “보안 포함 MVC 경로 통합”처럼 왜 전체 컨텍스트가 필요한지가 분명해야 한다. 이 해석은 Boot 문서의 full application context 성격과 slice 구분을 바탕으로 한 best practice다.

## 5. 언제 @SpringBootTest를 사용하지 않는가

### 5.1 순수 단위 테스트에는 사용하지 않는다

빈 하나의 로직만 검증하고 Spring 컨테이너가 필요 없다면 `@SpringBootTest`는 과도하다. Spring 문서는 테스트 지원이 통합 테스트에 강점을 가지지만, IoC 덕분에 단위 테스트도 쉽게 할 수 있다고 설명한다. 프로젝트에서는 순수 계산, 도메인 로직, 단일 서비스의 협력 객체 스텁/페이크 테스트에 `@SpringBootTest`를 기본 금지한다.

### 5.2 repository 전용 테스트에는 기본적으로 사용하지 않는다

Spring Boot는 `@DataJpaTest` 같은 데이터 접근 slice를 제공하고, JPA 테스트는 엔티티와 repository만 좁게 로딩하도록 설계되어 있다. 따라서 repository 전용 검증에 `@SpringBootTest`를 기본값으로 두면 범위가 과도하다. repository test 기준은 다음 문서에서 따로 상세히 다루지만, 이 문서 수준에서도 repository만 보려는 테스트에 full context는 기본 금지가 맞다.

### 5.3 MVC 계층만 검증하는 테스트에는 기본적으로 사용하지 않는다

Spring Boot는 Spring MVC controller 테스트에 `@WebMvcTest`를 제공한다고 설명한다. 따라서 controller request mapping, validation, status code, JSON binding, advice 같은 MVC 계층만 보려는 테스트에 `@SpringBootTest`를 기본값으로 쓰지 않는다. 전체 컨텍스트가 필요한 특별한 이유가 있을 때만 예외적으로 사용한다.

## 6. webEnvironment 기준

### 6.1 기본값은 목적에 맞는 가장 좁은 webEnvironment

Spring Boot는 `@SpringBootTest`의 `webEnvironment`로 `MOCK`, `RANDOM_PORT`, `DEFINED_PORT`, `NONE`을 제공한다고 설명한다. 프로젝트 기본 원칙은 가장 좁은 환경을 먼저 고르는 것이다. 웹 서버가 실제로 필요 없으면 서버를 띄우지 않는다.

### 6.2 NONE: non-web full context 테스트의 기본값

`NONE`은 `SpringApplication`으로 `ApplicationContext`를 로드하지만 웹 환경은 만들지 않는다. 따라서 웹 서버가 필요 없는 full-context 테스트, 예를 들어 서비스 통합 테스트나 단순 기동 테스트의 기본값으로 가장 적절하다.

### 6.3 MOCK: 실제 서버 없이 웹 애플리케이션을 통합 검증할 때 사용한다

`MOCK`은 기본값이며, web `ApplicationContext`를 로드하지만 내장 서버는 시작하지 않는다. Boot 문서는 이 모드가 `@AutoConfigureMockMvc` 또는 `@AutoConfigureWebTestClient`와 함께 mock 기반 웹 테스트에 적합하다고 설명한다. 프로젝트에서는 보안 필터, MVC 설정, Jackson, 예외 처리까지 포함하되 실제 포트는 필요 없는 경우 `MOCK`을 사용한다.

### 6.4 RANDOM_PORT: 실제 내장 서버와 실제 HTTP 왕복이 필요할 때 사용한다

`RANDOM_PORT`는 실제 `WebServerApplicationContext`를 만들고, 임의 포트에 내장 서버를 띄운다. 프로젝트에서는 실제 HTTP stack, 필터 체인, 포트 바인딩, serialization, client-server 상호작용 자체를 검증해야 할 때만 사용한다. 자동화 테스트에서는 `DEFINED_PORT`보다 충돌 위험이 적어 일반적으로 더 안전하다. 앞 문장은 Boot가 `RANDOM_PORT`와 `DEFINED_PORT`를 구분해 설명하는 점 위에 얹는 프로젝트 권장안이다.

### 6.5 DEFINED_PORT: 예외적 상황에서만 사용한다

`DEFINED_PORT`는 설정 파일 또는 기본 포트 8080으로 실제 서버를 띄운다. Boot 문서는 이 모드를 공식 지원하지만, 프로젝트에서는 고정 포트를 요구하는 외부 연동 테스트처럼 특별한 이유가 있을 때만 사용한다. 일반 테스트 스위트 기본값으로 두기에는 포트 충돌과 환경 의존성이 커질 수 있다. 이 판단은 Boot의 포트 모드 설명 위에 얹는 best practice다.

## 7. 트랜잭션 기준

### 7.1 같은 스레드 안에서 실행되는 @Transactional 테스트는 기본적으로 롤백된다

Spring TestContext Framework는 테스트 메서드에 `@Transactional`이 붙으면 테스트를 트랜잭션 안에서 실행하고, 기본적으로 종료 시 롤백한다고 설명한다. 따라서 same-thread 방식의 `@SpringBootTest`에서는 테스트 데이터 정리에 유용할 수 있다.

### 7.2 RANDOM_PORT / DEFINED_PORT에서는 테스트 메서드 롤백을 서버 처리까지 기대하지 않는다

Spring Boot는 `RANDOM_PORT`나 `DEFINED_PORT`에서는 실제 서버와 클라이언트가 별도 스레드에서 실행되므로, 테스트 메서드의 `@Transactional` 롤백이 서버 쪽 트랜잭션에는 적용되지 않는다고 명시한다. 프로젝트에서는 이 모드에서 “테스트 끝나면 DB가 자동 롤백될 것”이라는 기대를 금지한다.

## 8. slice 테스트와의 관계

### 8.1 slice가 가능하면 slice를 우선한다

Spring Boot는 test slices가 애플리케이션의 특정 부분만 테스트하도록 설계되었다고 설명한다. 프로젝트에서는 `@SpringBootTest`보다 좁은 slice가 정확히 요구사항을 만족하면 slice를 우선한다. full context는 필요 비용이 더 크기 때문이다.

### 8.2 여러 slice를 동시에 섞지 않는다

Spring Boot는 여러 `@…Test` slice annotation을 한 테스트에 함께 쓰는 것은 지원하지 않는다고 설명한다. 여러 조각이 동시에 필요하면 하나의 slice를 고르고, 다른 기능은 필요한 `@AutoConfigure…`를 수동으로 더하라고 안내한다. 프로젝트에서도 slice를 여러 개 겹치는 구조는 기본 금지다.

### 8.3 full context가 필요하지만 테스트 편의 기능도 원하면 @AutoConfigure…를 @SpringBootTest와 조합한다

Spring Boot는 `@AutoConfigure…` 계열 어노테이션을 `@SpringBootTest`와 조합할 수 있다고 설명한다. 따라서 전체 컨텍스트는 유지하되 `MockMvc`, `WebTestClient` 같은 테스트 편의 빈이 필요하면 이 조합을 사용한다.

## 9. 컨텍스트 캐시 기준

### 9.1 @SpringBootTest 변형을 최소화해 컨텍스트 캐시를 재사용한다

Spring TestContext Framework는 `ApplicationContext`를 static cache에 저장해 재사용한다고 설명한다. 따라서 properties, profiles, 임시 설정 클래스, 불필요한 커스텀 조합을 테스트마다 제각각 바꾸면 캐시 재사용이 줄고 전체 테스트가 느려질 수 있다. 프로젝트에서는 비슷한 목적의 `@SpringBootTest`는 같은 컨텍스트 구성을 공유하도록 정리한다.

## 10. 프로젝트 권장안

### 10.1 @SpringBootTest는 테스트 피라미드의 상단에 둔다

프로젝트 기본 전략은 다음과 같다.

- 순수 로직은 단위 테스트
- 기술 경계별 검증은 slice test
- 여러 레이어와 Boot 조립을 함께 확인해야 하는 경우에만 `@SpringBootTest`

이 규칙은 Spring Boot가 slices와 full-context test를 함께 제공하는 설계와 맞는 프로젝트 권장안이다.

### 10.2 non-web full-context 테스트의 기본값은 webEnvironment = NONE

실제 서버가 필요 없는데도 기본 `MOCK`이나 real server 모드를 쓰면 테스트 의도가 흐려질 수 있다. 프로젝트에서는 웹이 아닌 full-context 테스트는 `NONE`을 기본값으로 둔다. 이는 Boot가 `NONE`을 공식 지원한다는 점 위에 얹는 프로젝트 규칙이다.

### 10.3 웹 통합 테스트는 두 갈래로 나눈다

프로젝트 권장안은 다음과 같다.

- 실제 포트가 필요 없고 MVC/보안/직렬화 조합만 보면 된다 → `MOCK` + `@AutoConfigureMockMvc`
- 실제 서버와 실제 HTTP round-trip이 필요하다 → `RANDOM_PORT`

이 구분은 Boot의 `webEnvironment` 설명을 실무적으로 정리한 프로젝트 권장안이다.

## 11. 금지 규칙

다음은 기본 금지다.

- 순수 단위 테스트에 `@SpringBootTest` 사용
- repository 전용 테스트에 기본적으로 `@SpringBootTest` 사용
- MVC 슬라이스로 충분한데 full context를 올리는 것
- `RANDOM_PORT`/`DEFINED_PORT` 테스트에서 `@Transactional` 롤백을 서버 처리까지 기대하는 것
- 여러 slice annotation을 한 테스트에 동시에 붙이는 것
- 목적 없이 properties, profiles, 설정 클래스를 계속 바꿔 컨텍스트 캐시를 깨는 것
- full context가 필요한 이유를 설명하지 못하는 `@SpringBootTest` 추가

이 금지 규칙은 Spring Boot의 full-context testing/slice 문서와 Spring TestContext의 트랜잭션·컨텍스트 캐시 문서를 바탕으로 한 best practice다.

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 테스트는 정말 full application context가 필요한가?
- slice test로 줄일 수 없는가?
- `webEnvironment`가 목적에 비해 과하지 않은가?
- 정렬된 목적에 맞게 `NONE`, `MOCK`, `RANDOM_PORT`, `DEFINED_PORT`를 골랐는가?
- `RANDOM_PORT`/`DEFINED_PORT`에서 rollback 기대를 잘못 두고 있지 않은가?
- 컨텍스트 구성 변형을 최소화하고 있는가?
- 같은 목적의 테스트들이 컨텍스트 캐시를 재사용할 수 있는가?
