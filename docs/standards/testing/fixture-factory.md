# Fixture / Factory 기준

## 1. 목적

이 문서는 테스트 코드에서 사용하는 fixture와 factory를 어떤 기준으로 만들고 사용할지 정의한다.

이 문서의 목표는 다음과 같다.

- 테스트 데이터 준비를 중복 없이 재사용 가능하게 만든다
- fixture와 factory의 역할을 분리해 테스트 가독성을 높인다
- mutable shared state로 인한 테스트 간 간섭을 줄인다
- repository test, service test, `@SpringBootTest` 통합 테스트에서 테스트 데이터 준비 방식을 일관되게 만든다

JUnit Jupiter는 기본적으로 테스트 메서드마다 새로운 테스트 클래스 인스턴스를 만들어 테스트 간 상태 간섭을 줄이도록 설계되어 있고, Spring TestContext Framework는 테스트 인스턴스에 의존성을 주입해 fixture를 구성할 수 있게 한다. Spring Boot는 JPA 테스트에서 `TestEntityManager`를 보조 도구로 제공한다. 이 문서는 그런 공식 기능 위에, 프로젝트 차원의 fixture/factory 설계 규칙을 얹는 문서다.

## 2. 근거 수준

- Official: JUnit / Spring Framework / Spring Boot 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 테스트 lifecycle, test fixture DI, JPA test helper 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

중요하게, fixture / factory라는 용어 자체를 JUnit이나 Spring이 프로젝트 표준으로 정의해 주지는 않는다. 공식 문서는 테스트 인스턴스 lifecycle, 테스트 fixture에 대한 DI, JPA test helper를 제공할 뿐이고, 이 문서의 fixture/factory 구분은 그 위에 얹는 실무적 설계 규칙이다. 또한 이 문서에서 말하는 factory는 JUnit의 동적 테스트용 `@TestFactory`가 아니라, 테스트 데이터 생성 helper/factory를 뜻한다. JUnit의 `@TestFactory`는 런타임에 동적 테스트를 생성하는 별도의 기능이다.

## 3. 용어 정의

### 3.1 fixture

이 문서에서 fixture는 테스트가 읽기 쉬운 형태로 준비된 데이터/상태를 뜻한다. 예를 들어 “활성 사용자”, “삭제된 사용자”, “주문이 2개 있는 고객”처럼 시나리오 의미가 드러나는 준비물이 fixture다. 이 개념은 공식 프레임워크 용어라기보다 테스트 설계 용어이지만, JUnit의 per-method lifecycle과 Spring의 test fixture DI는 이런 준비물을 테스트마다 독립적으로 구성하는 데 맞춰져 있다.

### 3.2 factory

이 문서에서 factory는 새 테스트 데이터를 생성하는 helper를 뜻한다. fixture가 “이 테스트에서 필요한 상태 이름”에 가깝다면, factory는 “그 상태를 만들 수 있는 생성 도구”에 가깝다. 따라서 factory는 보통 매 호출마다 새 객체를 반환하고, fixture는 그 factory를 사용해 특정 시나리오를 설명하는 더 얇은 레이어가 된다. 이 구분은 공식 문서의 직접 규정이 아니라, JUnit의 테스트 격리 모델과 Spring 테스트 지원 위에 얹는 프로젝트 권장안이다.

## 4. 기본 원칙

### 4.1 fixture와 factory는 테스트 지원 코드이지, 테스트 대상이 아니다

fixture와 factory의 목적은 테스트를 짧게 만드는 것이 아니라 의도를 더 잘 드러내게 하는 것이다. Spring 테스트 문서는 DI가 테스트와 통합 테스트를 더 쉽게 만든다고 설명하지만, 그 목적은 wiring 편의이지 테스트 관심사를 숨기는 것이 아니다. 프로젝트에서는 fixture/factory가 테스트의 핵심 조건과 기대를 감추지 않아야 한다.

### 4.2 테스트에서 중요한 값은 숨기지 않고 드러낸다

fixture/factory는 반복되는 노이즈를 줄이기 위해 존재하지만, 테스트 결과를 바꾸는 핵심 입력까지 숨기면 테스트가 읽기 어려워진다. 따라서 프로젝트에서는 “이 테스트가 왜 통과/실패해야 하는가”를 결정하는 값은 테스트 본문에 남기고, 나머지 반복 필드만 fixture/factory가 채우게 한다. 이 규칙은 JUnit이 기본적으로 테스트 메서드 격리와 명확한 lifecycle을 제공한다는 점 위에 얹는 실무 best practice다.

### 4.3 기본값은 유효한 객체여야 한다

factory가 만드는 기본 객체는 특별한 목적이 없는 한 도메인상 유효한 상태여야 한다. invalid 상태가 필요하면 `invalidEmailUser()`, `deletedUser()`처럼 의도가 드러나는 별도 fixture/factory entrypoint로 표현한다. 이렇게 해야 테스트가 “무엇을 깨려는지”를 코드만 봐도 이해할 수 있다. 이 규칙은 공식 API가 직접 강제하는 것은 아니지만, Spring이 단위 테스트와 통합 테스트에서 DI를 통해 테스트 준비를 쉽게 하라고 설명하는 취지와 맞는 프로젝트 권장안이다.

### 4.4 mutable shared fixture는 기본 금지다

JUnit Jupiter의 기본 lifecycle은 `PER_METHOD`이며, 각 테스트 메서드 전에 새로운 테스트 인스턴스를 만든다. JUnit은 이것이 mutable test instance state로 인한 예기치 않은 부작용을 줄이기 위한 기본 동작이라고 설명한다. 프로젝트에서도 이 철학을 따라, mutable 엔티티나 변경 가능한 컬렉션을 static/shared fixture로 재사용하는 것을 기본 금지한다. 매 호출마다 새로운 객체를 만들어야 한다.

### 4.5 PER_CLASS lifecycle은 fixture 최적화 수단이 아니라 예외적 선택지다

JUnit은 `@TestInstance(PER_CLASS)`를 쓰면 같은 테스트 인스턴스를 재사용하게 되고, 이 경우 instance field 상태를 `@BeforeEach`나 `@AfterEach`에서 직접 정리해야 할 수 있다고 설명한다. 또한 기본 lifecycle을 바꾸는 것은 일관되지 않게 적용되면 fragile build를 만들 수 있다고 경고한다. 프로젝트에서는 fixture/factory 편의 때문에 `PER_CLASS`를 기본값으로 바꾸지 않는다.

## 5. fixture 기준

### 5.1 fixture 이름은 시나리오를 설명해야 한다

fixture 이름은 `user1`, `sampleOrder`, `data()`처럼 모호하면 안 되고, `activeUser()`, `deletedUser()`, `orderPendingPayment()`처럼 현재 테스트가 필요로 하는 상태를 설명해야 한다. fixture는 테스트 서사의 일부이므로, 생성 방식보다 상태 의미가 이름에 드러나야 한다. 이 규칙은 프로젝트 best practice다.

### 5.2 fixture는 최소한의 차이만 담아야 한다

fixture가 너무 많은 상태를 한 번에 끌고 오면 테스트마다 어떤 값이 핵심인지 구분하기 어려워진다. 따라서 `activeAdminUserWithExpiredPasswordAndTwoOrders()` 같은 거대한 fixture보다, 기본 유효 factory + 필요한 시나리오 fixture를 조합하는 방식을 선호한다. 이 규칙은 JUnit의 테스트 격리 모델과 Spring 테스트의 fixture DI 취지를 코드 가독성 관점으로 확장한 프로젝트 권장안이다.

### 5.3 fixture는 공유 상태를 캐시하지 않는다

fixture helper 내부에서 static mutable 객체를 재사용하거나, 이전 테스트에서 만든 엔티티를 보관했다가 다시 넘기지 않는다. JUnit 기본 lifecycle이 테스트 격리를 지향하는 이유와 어긋나기 때문이다. fixture는 매번 fresh object를 생성하거나, 적어도 immutable 값만 공유해야 한다.

### 5.4 persisted fixture와 transient fixture를 구분한다

JPA 테스트에서는 아직 저장되지 않은 객체와, DB에 persist/flush까지 된 객체의 의미가 다르다. Spring Boot의 `TestEntityManager`도 `persist`, `persistAndFlush`, `persistFlushFind` 같은 helper를 제공한다. 프로젝트에서는 `user()` 같은 transient fixture와 `persistedUser()` 같은 persisted fixture를 이름부터 구분하는 것을 권장한다.

## 6. factory 기준

### 6.1 factory는 호출할 때마다 새 객체를 반환한다

factory는 기본적으로 새 인스턴스 생성기여야 한다. JUnit의 per-method isolation 철학상, 테스트가 서로 영향을 주지 않으려면 각 테스트가 독립적인 객체를 받아야 한다. 따라서 factory는 static mutable singleton을 반환하거나 같은 엔티티 인스턴스를 재사용하지 않는다.

### 6.2 factory는 기본값을 채우고, 테스트는 중요한 차이만 override한다

좋은 factory는 도메인상 유효한 기본값을 제공하고, 테스트는 필요한 필드만 덮어쓴다. 이렇게 해야 테스트 본문이 짧아지고, 동시에 핵심 입력은 눈에 남는다. 프로젝트에서는 factory가 모든 필드를 강제로 매개변수로 받는 형태보다, sane defaults + override 조합을 선호한다. 이 규칙은 공식 문서의 직접 규정은 아니지만, Spring이 테스트에서 DI로 준비 부담을 줄이게 해 주는 방향과 맞는 실무 기준이다.

### 6.3 boolean flag 나열형 factory는 기본 금지다

`user(true, false, true, false)`처럼 boolean 파라미터가 늘어나는 factory는 테스트 의도를 숨긴다. 프로젝트에서는 의도가 다른 상태라면 별도 fixture entrypoint나 명시적 builder step으로 나누는 것을 기본값으로 둔다. 이 규칙은 테스트 가독성과 시나리오 설명력을 높이기 위한 프로젝트 권장안이다.

### 6.4 invalid factory는 명시적으로 이름 붙인다

유효한 기본 factory와 달리, 제약 위반이나 validation failure를 검증하는 invalid 객체는 `userWithInvalidEmail()`, `orderWithoutCustomer()`처럼 왜 invalid인지 이름에 드러나야 한다. repository test에서는 이런 invalid fixture를 `flush()`까지 가서 검증하는 경우가 많기 때문에, 이름이 더 중요하다. `TestEntityManager`가 persist/flush/find helper를 제공하는 점도 이런 테스트를 명시적으로 작성하도록 돕는다.

## 7. Spring 컨텍스트 테스트 기준

### 7.1 Spring test fixture DI는 허용되지만, fixture 자체를 bean으로 만들지는 않는다

Spring Framework는 테스트 인스턴스에 field, setter, constructor injection을 사용할 수 있다고 설명하고, test code에서는 field injection도 자연스러울 수 있다고 설명한다. 하지만 프로젝트에서는 fixture/factory 자체를 애플리케이션 빈으로 등록하는 것을 기본값으로 두지 않는다. 대부분의 fixture/factory는 `src/test` 안의 일반 테스트 지원 코드로 충분하다. Spring bean으로 올리는 것은 repository, clock, encoder처럼 실제 인프라 의존이 있는 경우에만 제한적으로 허용한다.

### 7.2 Spring 컨텍스트를 쓰는 테스트에서도 fixture/factory는 테스트 소스에 둔다

test fixture DI는 테스트 클래스에 이미 만들어진 bean을 주입하는 수단이지, 테스트 데이터를 애플리케이션 production bean처럼 관리하라는 뜻은 아니다. 프로젝트에서는 fixture/factory를 production source set에 두지 않고, 기본적으로 `src/test/java` 또는 테스트 전용 support 패키지에 둔다. 이는 Spring test fixture DI 공식 기능 위에 얹는 프로젝트 경계 규칙이다.

## 8. JPA / repository test 기준

### 8.1 JPA fixture는 영속성 상태를 의식해야 한다

JPA 테스트에서 객체가 새 객체인지, managed 상태인지, DB에 flush되었는지에 따라 의미가 달라진다. `TestEntityManager`는 바로 이런 테스트를 위해 `persist`, `flush`, `find`, `persistFlushFind` 같은 helper를 제공한다. 프로젝트에서는 repository test용 fixture/factory가 이 차이를 무시하지 않도록, `newUser()`, `persistedUser()`, `persistedUserAndClear()` 같은 식으로 상태를 구분해 제공하는 것을 권장한다.

### 8.2 DB round-trip 의미가 중요한 테스트에서는 flush/clear를 factory가 완전히 숨기지 않는다

fixture/factory가 너무 많은 것을 숨기면 repository test에서 중요한 `flush()`/`clear()` 타이밍이 보이지 않게 된다. JPA 테스트의 핵심은 종종 “실제 DB와 동기화한 뒤 다시 읽었을 때 무엇이 보이는가”이므로, persisted helper를 제공하더라도 테스트 본문에서 flush/clear가 왜 필요한지 설명 가능해야 한다. `TestEntityManager`가 제공하는 helper는 보조 도구이지, 테스트 의미를 가리는 추상화가 되어서는 안 된다.

## 9. 프로젝트 권장안

### 9.1 기본 구조

프로젝트의 기본 권장 구조는 다음과 같다.

- fixture: 시나리오 이름이 드러나는 얇은 helper
- factory: 기본 유효 객체를 생성하는 재사용 도구
- persisted factory: repository / `TestEntityManager`를 써서 DB 상태까지 준비하는 helper
- 테스트 본문: 핵심 override와 assertion을 직접 드러냄

이 구조는 JUnit의 per-method 격리, Spring test fixture DI, Spring Boot `TestEntityManager` 역할을 함께 고려한 프로젝트 표준이다.

### 9.2 파일/패키지 권장안

프로젝트에서는 fixture/factory를 기본적으로 테스트 소스에 두고, 필요하면 다음처럼 나눈다.

- `...testsupport.fixture`
- `...testsupport.factory`
- `...testsupport.builder`
- `...testsupport.persisted`

이는 공식 프레임워크 규칙은 아니지만, 테스트 지원 코드를 production 코드와 분리하고 책임을 드러내기 위한 프로젝트 권장안이다.

## 10. 금지 규칙

다음은 기본 금지다.

- mutable entity를 static/shared fixture로 재사용하는 것
- fixture/factory가 테스트의 핵심 입력을 숨기는 것
- boolean 나열형 factory로 상태 의미를 감추는 것
- invalid 상태를 모호한 이름의 기본 fixture로 섞어 두는 것
- persisted/transient 상태를 이름 없이 섞는 것
- repository test에서 flush/clear 의미를 factory가 완전히 감춰 버리는 것
- fixture/factory를 production source set에 두는 것
- fixture/factory를 애플리케이션 bean으로 무분별하게 등록하는 것
- `PER_CLASS` lifecycle을 fixture 편의 때문에 기본값처럼 사용하는 것

이 금지 규칙은 JUnit의 test instance lifecycle, Spring의 test fixture DI, Spring Boot의 JPA test helper 역할을 실무 규칙으로 압축한 것이다.

## 11. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- fixture 이름이 시나리오 의미를 설명하는가?
- factory는 매번 새 객체를 반환하는가?
- 기본 factory가 유효한 객체를 만드는가?
- invalid 상태는 별도 이름으로 드러나는가?
- persisted fixture와 transient fixture가 구분되는가?
- repository test에서 DB round-trip 의미가 중요한 지점이 테스트에 드러나는가?
- fixture/factory가 테스트 핵심 입력을 과하게 숨기지 않는가?
- 테스트 지원 코드가 production source가 아니라 test source에 있는가?
- `PER_CLASS`나 shared mutable state에 의존하지 않는가?
