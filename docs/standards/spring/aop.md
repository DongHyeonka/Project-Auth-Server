# AOP 사용 기준

## 목적

AOP는 비즈니스 로직을 숨기는 우회 수단이 아니라,  
여러 타입과 객체를 가로지르는 **cross-cutting concern** 을 모듈화할 때만 사용한다.

## 공식 의미

- Spring AOP는 OOP를 보완하는 방식이다.
- AOP의 핵심 단위는 class가 아니라 aspect다.
- Spring AOP는 proxy-based다.
- proxy-based AOP에서는 proxy를 통과하는 외부 호출만 advice가 적용된다.
- self-invocation은 기본적으로 advice가 적용되지 않는다.
- Spring은 AOP의 대표적 용도로 declarative transaction 같은 cross-cutting concern을 든다.
- pointcut은 더 작은 named pointcut으로 조합하는 것이 권장된다.
- 대부분의 경우 static pointcut이 충분하고 더 낫다.

## 기본 규칙

### 1. AOP는 cross-cutting concern에만 사용
다음은 AOP 후보가 된다.

- 공통 로깅
- 메트릭/트레이싱
- 권한 체크의 반복 경계
- 재시도/타이밍 측정
- 공통 감사(audit)
- 선언적 트랜잭션

다음은 AOP로 풀지 않는다.

- 핵심 비즈니스 흐름
- 상태 전이 정책
- 도메인 규칙
- 복잡한 분기 로직
- 외부 API orchestration

### 2. 기본값은 “명시적 코드”, 예외적으로 AOP
같은 기능을 명시적 코드로 더 잘 읽을 수 있으면 AOP를 쓰지 않는다.

기본:
- use case/service 안에서 명시적으로 보이는 흐름 우선
- 반복되는 횡단 관심사만 AOP로 추출

### 3. proxy-based 한계를 항상 전제로 한다
Spring AOP는 proxy 기반이므로 다음을 전제로 설계한다.

- proxy를 통과하는 외부 호출만 interception
- self-invocation은 적용되지 않음
- “같은 클래스 안에서 호출되면 aspect가 붙겠지”를 금지

### 4. self-invocation 해결을 위해 AOP 남용 금지
self-invocation 문제를 해결하려고 다음을 기본 금지한다.

- 자기 자신 proxy 주입
- `AopContext.currentProxy()` 의존
- 구현을 proxy semantics에 강하게 묶는 설계

기본 대응:
- 경계를 다시 분리
- 클래스를 분리
- 더 명시적인 구조로 변경

### 5. AOP는 경계가 뚜렷한 곳에만 적용
좋은 적용 지점:
- service/use-case public method
- controller 경계
- repository 경계
- 명시된 package/bean naming convention

지양:
- 너무 넓은 전체 패키지
- “일단 다 잡고 보자” 식 표현식
- private/internal 세부 구현까지 얽는 pointcut

### 6. pointcut은 작고 이름 있게 조합
공식 권장대로 pointcut은 작은 named pointcut을 조합해 만든다.

기본:
- package 범위 pointcut
- role 기반 pointcut
- public method pointcut
- bean naming 기반 pointcut

를 분리하고 조합한다.

### 7. 대부분 static pointcut 우선
동적 조건보다 static pointcut이 충분하면 static 쪽을 우선한다.
성능/이해도/예측 가능성이 더 좋다.

### 8. `@Around`는 최소화
`@Around`는 가장 강력하지만 가장 위험하다.
반환값/예외/호출 자체를 제어할 수 있으므로 꼭 필요할 때만 쓴다.

기본 우선순위:
- 단순 전처리 -> `@Before`
- 정상 반환 후 후처리 -> `@AfterReturning`
- 예외 기록/번역 -> `@AfterThrowing`
- 무조건 정리 -> `@After`
- 호출 제어/타이밍/재시도 등 정말 필요할 때만 `@Around`

### 9. advice 안에서 비즈니스 의미를 새로 만들지 않는다
advice는 보조 concern을 수행해야 한다.

금지:
- 상태 전이 결정
- 비즈니스 실패를 성공처럼 바꾸기
- 핵심 정책 우회
- controller/service가 해야 할 결정을 aspect에서 대신하기

### 10. 예외를 숨기지 않는다
AOP에서 예외를 잡더라도 기본은:
- 기록
- 문맥 추가
- 그대로 전파
중 하나다.

금지:
- 예외 삼키기
- 정상값으로 은폐
- 실패를 조용히 무시

### 11. 트랜잭션 대체 수단으로 일반 AOP를 남용하지 않는다
선언적 트랜잭션은 Spring이 제공하는 표준 메커니즘을 우선 사용한다.
일반 custom aspect로 transaction semantics를 흉내 내지 않는다.

### 12. AOP는 observability / policy enforcement에 더 적합
프로젝트에서 AOP는 아래 유형에 더 적합하다.

- 실행 시간 측정
- 공통 로깅
- 감사 기록
- annotation 기반 정책 강제
- 공통 예외 기록

복잡한 use case orchestration에는 부적합하다.

### 13. pointcut 범위는 문서화 가능해야 한다
pointcut을 보고 아래를 설명할 수 있어야 한다.

- 어디에 적용되는가
- 왜 거기에만 적용되는가
- 새 코드가 추가되면 어떤 naming/package 규칙으로 포함되는가

설명하기 어려우면 범위가 너무 넓거나 모호한 것이다.

### 14. bean naming / package convention을 설계와 함께 쓴다
Spring 공식 문서가 bean PCD나 package 기반 pointcut 예시를 드는 것처럼,
AOP를 쓸 거면 package 구조나 bean naming convention이 일정해야 한다.

즉:
- `*Service`
- `..application..`
- `..infrastructure..`
같은 규칙은 pointcut과 함께 관리한다.

## 프로젝트 기준 요약

- AOP는 cross-cutting concern에만 사용
- 기본값은 명시적 코드
- Spring AOP는 proxy-based라는 점을 전제로 설계
- self-invocation 기대 금지
- pointcut은 작고 이름 있게 조합
- 대부분 static pointcut 우선
- `@Around` 최소화
- advice에서 비즈니스 의미를 만들지 않음
- 예외를 숨기지 않음