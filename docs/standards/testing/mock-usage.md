# Mock 사용 기준

## 1. 목적

이 문서는 테스트에서 mock, spy, Spring 컨텍스트 bean override mock을 언제 사용하고 언제 사용하지 말아야 하는지 정의한다.

이 문서의 목표는 다음과 같다.

- mock을 협력 객체 경계에만 사용하도록 제한한다
- 단위 테스트용 Mockito mock과 Spring 컨텍스트용 bean override mock을 구분한다
- spy, static mock, 과도한 interaction verification 같은 취약한 패턴을 줄인다
- 현재 Spring 기준에 맞게 `@MockBean`/`@SpyBean` 대신 `@MockitoBean`/`@MockitoSpyBean` 사용 원칙을 정한다

Spring Boot는 현재 Spring Framework의 `@MockitoBean`과 `@MockitoSpyBean`을 Spring 테스트 컨텍스트 안의 bean override 수단으로 안내하고 있고, Spring Boot의 기존 `@MockBean` / `@SpyBean`은 3.4.0부터 deprecated 되었으며 4.0.0에서 제거되었다고 설명한다. 본 프로젝트는 Spring Boot 4.0.3을 사용하므로 해당 어노테이션은 더 이상 컴파일되지 않는다.

## 2. 근거 수준

- Official: Spring Framework / Spring Boot / Mockito 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 Spring Framework의 `@MockitoBean` / `@MockitoSpyBean`, Spring Boot의 테스트 문서와 deprecation API, Mockito의 `MockitoExtension`, strict stubbing, spy, `verifyNoMoreInteractions()` 관련 문서를 기준으로 작성한다. Mockito는 `MockitoExtension`이 mocks를 초기화하고 strict stubbings를 처리한다고 설명하고, `STRICT_STUBS`를 highly recommended라고 설명한다. 또한 spy는 carefully and occasionally 사용해야 한다고 경고하고, `verifyNoMoreInteractions()`를 모든 테스트마다 쓰는 것은 권장하지 않는다고 설명한다.

## 3. 기본 원칙

### 3.1 mock은 “테스트하고 싶은 대상”이 아니라 “대상이 의존하는 외부 협력자”에만 사용한다

mock은 테스트 대상 자체를 대신하는 도구가 아니라, 대상이 호출하는 다른 경계를 제어하기 위한 도구다. Mockito가 제공하는 mock/spy/verification 도구는 협력 객체를 대체하거나 관찰하는 데 목적이 있고, Spring의 `@MockitoBean`도 `ApplicationContext` 안의 bean을 override 하는 기능으로 설명된다. 프로젝트에서는 mock을 “내가 검증하려는 클래스”가 아니라 “내가 검증하려는 클래스가 호출하는 외부 의존성”에만 사용한다.

### 3.2 mock은 단순하게, 적게 사용한다

Mockito는 non-standard mock settings는 too often 쓰지 말라고 설명하고, 테스트가 너무 많은 mock에 의존하면 코드를 단순하게 리팩터링하는 편이 낫다고 시사한다. 프로젝트에서도 mock 개수가 많아질수록 테스트 대상이 너무 많은 책임을 가진 신호로 본다. 기본 원칙은 적은 수의 단순한 mock이다.

### 3.3 strict stubbing을 기본값으로 본다

Mockito는 strict stubbing이 cleaner tests, reduced duplication, improved debuggability를 주며 `STRICT_STUBS`를 highly recommended라고 설명한다. 또한 `MockitoExtension`은 mocks 초기화와 strict stubbings 처리를 담당한다고 설명한다. 프로젝트 기본값은 “쓰이지 않는 stub을 허용하는 느슨한 테스트”가 아니라 strict stubbing 기준의 테스트다.

## 4. 언제 mock을 사용하는가

### 4.1 순수 단위 테스트에서 외부 협력자를 대체할 때 사용한다

DB, 메시지 브로커, 외부 API client, 메일 발송기, 파일 저장기처럼 테스트 대상이 호출하는 외부 협력자를 실제로 띄우고 싶지 않을 때 mock이 적합하다. 이때는 Spring 컨텍스트 없이 `MockitoExtension`과 `@Mock`을 사용하는 순수 단위 테스트가 기본이다. Mockito는 `MockitoExtension`이 JUnit Jupiter용 확장이라고 설명한다.

### 4.2 interaction 자체가 의미인 경계에서 사용한다

어떤 메서드가 “무엇을 반환했는가”보다 “외부 협력자에게 어떤 호출을 했는가”가 의미인 경우가 있다. 예를 들어 이벤트 발행, 알림 전송, 외부 client 호출, retry 없이 1회만 위임해야 하는 adapter 경계가 그렇다. 이런 경우 mock verification이 적합하다. 다만 Mockito도 `verifyNoMoreInteractions()`를 모든 테스트마다 쓰는 것은 권장하지 않는다고 하므로, 프로젝트에서는 의미 있는 interaction만 검증한다.

### 4.3 Spring 컨텍스트 테스트에서는 bean override가 정말 필요할 때만 사용한다

`@MockitoBean`과 `@MockitoSpyBean`은 테스트의 `ApplicationContext` 안에서 bean을 mock/spy로 override하는 기능이다. 따라서 `@SpringBootTest`, slice test 같은 컨텍스트 테스트에서 특정 bean만 대체해야 할 때 적합하다. 하지만 이것은 “컨텍스트를 띄운 상태”를 전제로 하므로, 순수 단위 테스트에서 기본값이 되어서는 안 된다.

## 5. 언제 mock을 사용하지 않는가

### 5.1 엔티티, 값 객체, DTO에는 기본적으로 mock을 쓰지 않는다

엔티티, 값 객체, DTO는 테스트 대상 도메인 모델이므로 실제 객체를 만들어 쓰는 편이 더 자연스럽다. Mockito의 partial mock/spy 관련 문서도 partial mock이 대체로 설계 냄새라고 설명한다. 프로젝트에서는 도메인 모델을 mock으로 대체하지 않고, 실제 fixture/factory로 만든다.

### 5.2 repository test에서는 repository 자체를 mock하지 않는다

repository test의 목적은 DB 의미, 매핑, query, 제약을 검증하는 것이다. 이 문맥에서 repository를 mock으로 바꾸면 영속성 경계를 검증하지 못한다. 따라서 repository test는 `@DataJpaTest`와 실제 repository/DB를 사용하고, repository를 mock하는 것은 service 단위 테스트에서만 허용한다. 이 기준은 앞서 정한 repository test 문서와 일관된 프로젝트 규칙이다.

### 5.3 @SpringBootTest가 필요한 이유가 없는 테스트에서는 Spring bean mock을 쓰지 않는다

Spring Boot는 slice test와 full application context test를 구분해 제공한다. 따라서 Spring 컨텍스트가 굳이 필요 없는 테스트에서 `@MockitoBean`까지 사용하면 테스트가 과도하게 무거워진다. 프로젝트에서는 Spring bean mock보다 plain Mockito mock을 먼저 검토한다.

## 6. 단위 테스트에서의 기본 사용 기준

### 6.1 기본 조합은 MockitoExtension + @Mock

JUnit Jupiter 기반 Mockito 테스트의 기본 조합은 `@ExtendWith(MockitoExtension.class)`와 `@Mock`이다. Mockito는 `MockitoExtension`이 mocks를 초기화하고 strict stubbings를 처리한다고 설명한다. 프로젝트에서도 순수 단위 테스트의 기본 시작점은 이 조합이다.

### 6.2 @InjectMocks는 편의 수단으로만 사용한다

Mockito는 `@InjectMocks`가 constructor injection → setter injection → field injection 순서로 mock을 주입하려고 시도한다고 설명한다. 프로젝트에서는 `@InjectMocks`를 금지하지는 않지만, 테스트 대상 생성이 중요한 테스트에서는 명시적 생성자 호출을 더 선호한다. 그래야 의존성이 바뀌었을 때 테스트 코드에서 더 분명하게 드러난다. `@InjectMocks`는 보일러플레이트를 줄이는 편의 수단으로만 사용한다.

### 6.3 mock은 필요한 호출만 stub한다

Mockito는 strict stubbing이 cleaner tests를 만든다고 설명한다. 프로젝트에서는 미래를 대비한 과잉 stub, “혹시 몰라서 미리 깔아 두는 stub”, 실제로 사용되지 않는 stub을 금지한다. 테스트는 현재 시나리오에 필요한 stub만 가져야 한다.

## 7. verification 기준

### 7.1 state verification이 충분하면 interaction verification을 남발하지 않는다

mock verification은 유용하지만, 모든 테스트를 “호출 횟수 검사” 중심으로 만들 필요는 없다. Mockito도 `verifyNoMoreInteractions()`를 모든 테스트마다 쓰는 것은 권장하지 않는다고 설명한다. 프로젝트에서는 반환값/상태 변화로 충분한 테스트라면 그쪽을 우선하고, interaction 검증은 외부 경계 의미가 분명할 때만 사용한다.

### 7.2 verifyNoMoreInteractions()는 기본 금지다

Mockito는 `verifyNoMoreInteractions()`를 every test method에 쓰는 것을 권장하지 않는다고 분명히 말한다. 프로젝트에서는 이 메서드를 기본 assertion처럼 붙이지 않는다. 정말로 “추가 호출이 있으면 안 된다”가 비즈니스 의미인 경우에만 제한적으로 사용한다.

### 7.3 ArgumentCaptor는 verification을 완성하는 용도로만 사용한다

Mockito는 `ArgumentCaptor`를 verification과 함께 쓰는 것을 권장하고, stubbing에 쓰면 가독성과 defect localization이 나빠질 수 있다고 설명한다. 프로젝트에서도 `ArgumentCaptor`는 호출된 인자의 값을 마지막에 확인하는 용도로만 사용하고, stub 조건을 억지로 만드는 데는 기본적으로 사용하지 않는다.

## 8. spy 기준

### 8.1 spy는 기본 선택지가 아니다

Mockito는 real spy를 carefully and occasionally 사용하라고 설명하고, partial mock은 대체로 code smell이며 새롭고 잘 설계된 코드에는 권하지 않는다고 말한다. 프로젝트에서도 spy는 기본 선택지가 아니라 레거시 코드, 3rd-party 인터페이스, 점진적 리팩터링 같은 예외 상황에서만 사용한다.

### 8.2 spy stubbing에는 when(...)보다 doReturn(...) 계열을 우선한다

Mockito는 spy에서 `when(spy.method())`가 실제 메서드를 호출해 부작용을 일으킬 수 있으므로, `doReturn` / `doThrow` / `doNothing` 같은 계열을 고려하라고 설명한다. 프로젝트에서는 spy를 써야 한다면 stub 방식도 `doReturn(...).when(spy)...` 기본값으로 둔다.

### 8.3 @MockitoSpyBean은 더 신중히 쓴다

Spring의 `@MockitoSpyBean`은 기존 bean 인스턴스를 감싸는 방식이고, scoped proxy에는 사용할 수 없으며, non-singleton bean을 spy해도 singleton처럼 취급될 수 있다. 따라서 프로젝트에서는 `@MockitoSpyBean`을 넓은 컨텍스트 테스트에서 기본값으로 두지 않는다. 정말로 실제 bean 동작 일부만 감시해야 할 때만 제한적으로 사용한다.

## 9. Spring 컨텍스트에서의 mock 기준

### 9.1 새 기준은 @MockitoBean, @MockitoSpyBean

Spring Framework는 `@MockitoBean`과 `@MockitoSpyBean`을 테스트의 `ApplicationContext` bean override 용도로 제공한다. Spring Boot 문서도 이 어노테이션들을 안내하고 있다. 프로젝트에서는 Spring 컨텍스트 테스트에서 bean override가 필요하면 이 둘을 기본값으로 사용한다.

### 9.2 @MockBean, @SpyBean은 신규 코드 기본값으로 쓰지 않는다

Spring Boot API 문서는 `@MockBean`과 관련 Boot Mockito 테스트 지원이 3.4.0부터 deprecated 되었고, 4.0.0에서 제거되었으며 `MockitoBean`/`MockitoSpyBean`으로 대체하라고 설명한다. 본 프로젝트는 Spring Boot 4.0.3 기반이므로 `@MockBean`/`@SpyBean`은 더 이상 사용 가능하지 않다. 신규 테스트는 `@MockitoBean`/`@MockitoSpyBean`을 사용하고, 기존 테스트도 동일하게 이전한다.

### 9.3 @MockitoBean은 bean override가 필요한 테스트에만 사용한다

`@MockitoBean`은 bean을 `REPLACE_OR_CREATE` 전략으로 override하고, `enforceOverride = true`를 주면 반드시 기존 bean이 있어야만 교체하도록 바꿀 수 있다. 프로젝트에서는 bean이 없으면 새 mock을 조용히 만들어 버리는 기본 동작이 테스트 의도를 흐릴 수 있으므로, “반드시 기존 bean을 대체해야 한다”는 테스트에는 `enforceOverride = true`를 검토한다.

### 9.4 같은 bean을 mock하는 테스트는 필드 이름과 qualifier를 일관되게 유지한다

Spring Framework는 field 이름이나 qualifier가 컨텍스트 분리에 영향을 줄 수 있고, 같은 bean을 여러 테스트에서 mock/spy할 때 필드 이름을 일관되게 유지하면 불필요한 새로운 `ApplicationContext` 생성을 줄일 수 있다고 설명한다. 프로젝트에서는 컨텍스트 캐시를 깨지 않기 위해 같은 bean mock 필드 이름을 가능하면 통일한다.

### 9.5 non-singleton bean mock/spy는 기본 금지다

Spring Framework는 `@MockitoBean`으로 non-singleton bean을 mock하면 singleton mock으로 대체되고, `@MockitoSpyBean`으로 non-singleton bean을 spy해도 singleton처럼 취급된다고 설명한다. 프로젝트에서는 prototype/scoped bean override mock을 기본 금지하고, 정말 필요하면 테스트 구조를 다시 설계하는 쪽을 우선한다.

## 10. static mock 기준

### 10.1 static mock은 예외적이고 짧게 사용한다

Mockito는 `MockedStatic`이 활성화된 정적 mock을 나타내며, 그 mock이 생성된 thread에만 영향을 주고, 다른 thread와 동시에 쓰는 것은 안전하지 않다고 설명한다. 프로젝트에서는 static mocking을 레거시나 외부 라이브러리 래핑 같은 예외 상황에서만 허용하고, try-with-resources로 scope를 매우 짧게 제한한다.

### 10.2 새 코드 설계에서는 static mock 대신 의존성 분리를 우선한다

static mock은 가능하더라도 thread-scoped이고 테스트를 더 취약하게 만들 수 있다. 프로젝트에서는 새 코드에서 시간, UUID, 외부 유틸 호출 같은 요소를 static method로 직접 부르기보다 bean/port로 분리해서 plain mock으로 대체 가능하게 만드는 것을 기본값으로 둔다. 이 부분은 Mockito의 static mock 제약 위에 얹는 프로젝트 권장안이다.

## 11. 프로젝트 권장안

### 11.1 기본 선택 순서

프로젝트의 기본 선택 순서는 다음과 같다.

- mock 없이 실제 객체로 테스트 가능하면 그렇게 한다
- 외부 협력자만 plain Mockito mock으로 대체한다
- Spring 컨텍스트가 정말 필요하면 `@MockitoBean`을 사용한다
- spy는 예외적으로만 사용한다
- static mock은 마지막 수단으로만 사용한다

이 순서는 Mockito가 spy/partial mock을 신중히 쓰라고 경고하는 점과, Spring이 bean override mock을 별도 기능으로 제공하는 점을 함께 반영한 프로젝트 규칙이다.

### 11.2 신규 Spring 테스트는 @MockitoBean 기준으로 작성한다

Spring Boot 3.4+ 기준에서는 기존 Boot `@MockBean` 계열보다 Spring Framework `@MockitoBean` 계열이 현재 공식 방향이다. 프로젝트의 신규 컨텍스트 테스트는 이 기준을 따른다.

### 11.3 단위 테스트는 strict, 명시적, 짧게 유지한다

프로젝트 단위 테스트 기본값은 다음과 같다.

- `MockitoExtension`
- strict stubbing
- 최소 stub
- 의미 있는 verification만 수행
- 가능하면 명시적 생성자 주입
- spy / static mock / deep stub 회피

Mockito는 strict stubbing을 강하게 권장하고, deep stubs와 partial mocks를 regular clean code에서는 드물게만 써야 한다고 설명한다.

## 12. 금지 규칙

다음은 기본 금지다.

- 테스트 대상 자체를 mock하는 것
- 엔티티, 값 객체, DTO를 기본적으로 mock하는 것
- repository test에서 repository를 mock하는 것
- 모든 테스트에 `verifyNoMoreInteractions()`를 습관적으로 붙이는 것
- spy를 기본 선택지처럼 사용하는 것
- spy에서 `when(spy.method())`로 실제 메서드 부작용을 일으키는 것
- Spring 컨텍스트 테스트 신규 코드에 `@MockBean` / `@SpyBean`을 기본값으로 사용하는 것
- prototype/scoped bean을 무심코 `@MockitoBean` / `@MockitoSpyBean`으로 override하는 것
- static mock을 긴 scope로 유지하거나 병렬 테스트에서 안전하다고 가정하는 것

이 금지 규칙은 Mockito의 spy/verification/static mock 주의사항과 Spring의 bean override 문서를 실무 규칙으로 압축한 것이다.

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 mock은 테스트 대상이 아니라 외부 협력자를 대체하고 있는가?
- plain unit test라면 Spring 컨텍스트 없이 `MockitoExtension`으로 충분한가?
- stub은 현재 시나리오에 필요한 것만 있는가?
- state verification으로 충분한데 interaction verification을 남발하고 있지 않은가?
- `verifyNoMoreInteractions()`가 정말 필요한 의미를 가지는가?
- spy를 쓰는 이유가 레거시/부분 대체 같은 예외 상황으로 설명되는가?
- Spring 컨텍스트 mock이라면 `@MockitoBean` / `@MockitoSpyBean`을 사용하고 있는가?
- 같은 bean mock의 field name/qualifier를 테스트 간 일관되게 유지하고 있는가?
- non-singleton bean override나 scoped proxy spy 같은 위험한 경우를 피했는가?
- static mock이 정말 마지막 수단인가?
