# dependency injection 기준

## 목적

의존성 주입은 객체가 자신의 협력 객체를 직접 생성하거나 찾지 않게 하여,
- 결합도를 낮추고
- 테스트를 쉽게 하며
- 초기화 상태를 더 명확하게 만드는 데 사용한다.

## 공식 의미

- Spring DI는 객체가 의존성을 생성자 인자, factory method 인자, 또는 setter/config method로 선언하면 컨테이너가 주입하는 방식이다.
- Spring은 constructor-based DI와 setter-based DI를 지원한다.
- Spring 팀은 일반적으로 constructor injection을 권장한다.
- constructor injection은 필수 의존성의 non-null 보장과 fully initialized state를 더 쉽게 만든다.
- setter injection은 주로 optional dependency 또는 재설정 가능한 dependency에 적합하다.
- field injection은 production code에서는 권장되지 않는다.
- 생성자가 하나뿐인 경우 Spring은 `@Autowired` 없이도 그 생성자를 사용할 수 있다.

## 기본 규칙

### 1. 기본값은 constructor injection
application code의 기본 DI 방식은 생성자 주입이다.

이유:
- 필수 의존성이 명확하다
- 객체가 생성 직후 완전한 상태가 된다
- final field 사용이 가능하다
- 테스트에서 plain constructor 호출이 쉽다

### 2. 필수 의존성은 생성자로만 받는다
다음은 생성자로만 주입한다.

- business collaborator
- repository / port
- external client
- policy / strategy
- configuration object
- mapper / validator / assembler 중 필수 협력 객체

필수 의존성을 setter/field로 받지 않는다.

### 3. 선택 의존성만 setter/config method injection 검토
setter 또는 config method injection은 아래일 때만 검토한다.

- optional dependency
- reasonable default가 있는 경우
- 재설정/reconfiguration 가능성이 실제로 필요한 경우
- legacy / third-party class 구조상 생성자 주입이 적합하지 않은 경우

기본값은 아니다.

### 4. production code field injection 금지
production code에서는 field injection을 사용하지 않는다.

이유:
- 의존성이 시그니처에 드러나지 않는다
- plain unit test가 불편해진다
- final field 사용이 어렵다
- partially initialized state 위험을 키운다

예외:
- 테스트 클래스
- framework가 직접 관리하는 극히 제한적 특수 케이스

### 5. 단일 생성자면 `@Autowired` 생략 가능
생성자가 하나뿐인 bean class는 `@Autowired`를 굳이 붙이지 않아도 된다.

기본:
- single constructor -> annotation 생략 가능
- 여러 생성자면 의도를 분명히 해야 한다

### 6. 의존성은 lookup하지 않는다
bean은 자신의 dependency를 직접 찾지 않는다.

금지:
- `applicationContext.getBean(...)`
- service locator 패턴
- static holder 통해 bean 가져오기

예외:
- 아주 제한된 framework integration
- truly dynamic lookup이 필요한 infrastructure 경계

기본은 constructor/setter 주입이다.

### 7. 생성자 인자가 많으면 DI 스타일이 아니라 책임 분해 문제를 먼저 본다
Spring 공식 문서도 constructor parameter가 많으면 code smell로 본다.

기본 판단:
- 5~7개 이상으로 커지면 책임 과다를 의심
- 하위 collaborator 분리
- policy object 분리
- orchestration 분리
- config object 묶기
를 먼저 검토한다

“setter로 바꿔서 숨기기”로 해결하지 않는다.

### 8. final field 우선
constructor injection을 쓴다면 의존성 필드는 가능한 한 final로 둔다.

이유:
- 불변성 강화
- 초기화 상태 명확화
- 재주입/변경 여지 축소

### 9. optional dependency는 명시적으로 표현
optional dependency는 다음 방식 중 하나를 명시적으로 선택한다.

- setter injection
- `ObjectProvider<T>`
- nullable/optional parameter를 가진 config method
- reasonable default를 가진 생성자/팩토리 구성

필수와 선택을 섞어 모호하게 만들지 않는다.

### 10. 컬렉션/다중 구현 주입은 의도를 분명히
여러 bean이 한 인터페이스를 구현할 때는:
- `List<T>`
- `Map<String, T>`
- `@Qualifier`
- `@Primary`
등을 통해 의도를 분명히 한다.

“우연히 하나만 있으니까 된다”에 기대지 않는다.

### 11. configuration properties는 raw value보다 객체로 주입
관련 설정값이 여러 개면 primitive/string 여러 개를 직접 주입하지 말고, configuration properties 객체로 묶어 주입하는 쪽을 우선 검토한다.

### 12. bean 간 순환 의존은 기본 금지
Spring은 constructor circular dependency를 문제로 보고, setter injection으로 우회는 가능하지만 권장하지 않는다.

기본:
- 순환 구조를 리팩터링으로 제거
- 책임 재배치
- 이벤트/포트/분리된 collaborator 도입 검토

setter로 억지 우회하지 않는다.

### 13. framework 관리 대상과 plain object를 구분
모든 객체가 DI 대상은 아니다.

기본:
- Spring bean 협력은 DI
- value object / domain entity / DTO / 단순 계산 객체는 plain object 생성 유지

### 14. 테스트도 같은 원칙을 따르되, 테스트 클래스 field injection은 허용 가능
Spring 공식 문서는 테스트에서는 field injection이 자연스러울 수 있다고 설명한다.

기본:
- production code -> constructor injection
- test class -> Spring test fixture에서는 field injection 허용 가능
- 하지만 application code 자체는 계속 constructor injection 유지

## 프로젝트 기준 요약

- 기본 DI 방식은 constructor injection
- 필수 의존성은 생성자
- optional dependency만 setter/config method 검토
- production code field injection 금지
- single constructor면 `@Autowired` 생략 가능
- dependency lookup 금지
- 생성자 인자 과다는 책임 분해 신호
- final field 우선
- circular dependency 우회보다 구조 수정 우선
