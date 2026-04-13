# Validation Location 기준

## 1. 목적

이 문서는 “무엇을 어디서 검증할 것인가”를 정의한다.

이 문서의 목표는 다음과 같다.

- request shape 검증과 business rule 검증을 구분한다.
- web validation, application validation, domain invariant enforcement의 위치를 명확히 한다.
- Bean Validation 애노테이션을 어디에 써야 하고 어디에 의존하면 안 되는지 정한다.
- validation이 filter, interceptor, advice 같은 잘못된 지점으로 새어 나가지 않게 한다.

## 2. 근거 수준

- Official: Spring Framework 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 문서의 제약 위에 실무적으로 널리 쓰이는 구조를 결합한 내용
- Project Recommendation: 이 프로젝트 구조에 맞춘 규칙

## 3. 기본 원칙

### 3.1 검증은 웹 계층 전용이 아니다

Spring은 validation이 웹 계층에 묶여 있어서는 안 되며, Spring Validator는 애플리케이션의 모든 계층에서 사용할 수 있다고 설명한다. 따라서 이 프로젝트에서도 validation을 “controller에서 끝나는 일”로 보지 않는다.

프로젝트 규칙:

- web validation은 입력 경계 검증
- application validation은 use case 실행 전제 검증
- domain validation은 도메인 불변식 보장
- 외부 연동 validation은 외부 계약 준수 확인

으로 나눈다.

### 3.2 한 곳의 검증만 믿지 않는다

프로젝트 규칙:

- controller validation은 잘못된 HTTP 입력을 빨리 거르는 역할이다.
- 그러나 domain invariant를 controller validation에 의존하지 않는다.
- 핵심 규칙은 domain 또는 application에서 다시 보장한다.

즉, “controller에서 @Valid 붙였으니 충분하다”를 금지한다.

### 3.3 request model과 domain model을 분리한다

Spring 공식 문서는 웹 데이터 바인딩에서 전용 model object를 사용하는 것이 좋고, JPA/Hibernate entity 같은 domain model을 직접 노출하지 말라고 권장한다. 또한 constructor binding 또는 allowedFields 같은 바인딩 통제를 검토하라고 안내한다.

프로젝트 규칙:

- request binding 대상은 전용 request DTO / form / command model이다.
- entity, aggregate, domain object를 @RequestBody / @ModelAttribute 바인딩 대상으로 쓰지 않는다.
- “웹 입력용 객체”와 “도메인 객체”는 분리한다.

## 4. 위치별 검증 규칙

### 4.1 Presentation(Web) 레이어

무엇을 여기서 검증하나

controller에서는 다음을 검증한다.

- 필수값 존재 여부
- 문자열 길이, blank 여부, 숫자 범위
- enum/format/basic pattern
- request DTO의 필드 단위 구조 검증
- 단일 요청 객체 안에서 해결 가능한 단순 cross-field 검증
- path variable / request param / header의 기본 제약

Spring MVC는 @RequestBody, @ModelAttribute, @RequestPart에 @Valid 또는 @Validated를 붙여 개별 객체 검증을 수행할 수 있고, 메서드 파라미터에 직접 @NotBlank, @Min 같은 constraint를 두면 method validation이 적용된다. 상황에 따라 MethodArgumentNotValidException 또는 HandlerMethodValidationException이 발생할 수 있다.

무엇을 여기서 검증하지 않나

controller에서는 다음을 검증하지 않는다.

- DB 조회가 필요한 business rule
- 인증 이후 사용자 상태 기반 정책
- aggregate 간 일관성
- 도메인 불변식의 최종 보장
- 외부 시스템 현재 상태에 의존하는 검증

이런 검증은 application 또는 domain으로 올린다.

controller 규칙

프로젝트 규칙:

- @RequestBody DTO에는 Bean Validation을 적극 사용한다.
- request param / path variable / request header의 단순 제약은 메서드 파라미터에 직접 건다.
- controller는 validation 실패를 ApiResult 규약으로 변환만 하고, 검증 정책 자체를 많이 품지 않는다.
- request DTO는 transport schema를 표현하는 객체이지 domain object가 아니다.

### 4.2 Application 레이어

무엇을 여기서 검증하나

application service / use case에서는 다음을 검증한다.

- use case 실행 전제조건
- 여러 입력 조합에 대한 정책 검증
- repository 조회나 외부 상태 조회가 필요한 검증
- “이 요청을 지금 수행해도 되는가”에 대한 오케스트레이션 수준 판단

Spring은 validation이 웹 계층에만 속하지 않는다고 설명하므로, 이런 종류의 검증을 application 계층으로 올리는 것은 Spring 철학과도 맞다.

프로젝트 규칙:

- application validation은 보통 명시적 코드로 작성한다.
- 복잡한 business rule을 Bean Validation 애노테이션으로 숨기지 않는다.
- application validation 실패는 domain/application 예외로 표현하고, web layer는 이를 ApiResult로 번역한다.

@Validated on service 사용 기준

Spring은 Bean Validation의 method validation을 MethodValidationPostProcessor와 @Validated를 통해 Spring bean에 적용할 수 있으며, 이는 AOP proxy에 의존한다고 설명한다. 따라서 proxy 한계가 있다.

프로젝트 규칙:

- service의 @Validated는 재사용 가능한 bean 경계 검증에 한해 제한적으로 허용한다.
- self-invocation, proxy bypass, 내부 private method 호출에 기대지 않는다.
- 핵심 business rule enforcement를 service method validation 하나에만 맡기지 않는다.
- 단순 null/size/precondition 보조 수단으로는 쓸 수 있지만, 복잡한 도메인 정책의 주 수단으로는 쓰지 않는다.

### 4.3 Domain 레이어

무엇을 여기서 검증하나

domain에서는 다음을 보장한다.

- value object 생성 조건
- entity/aggregate invariant
- 도메인 행위 수행 가능 조건
- “이 상태의 도메인 객체가 존재해도 되는가”에 대한 규칙

이 항목은 프로젝트 아키텍처 규칙이다.

프로젝트 규칙:

- domain invariant는 생성자, factory method, 도메인 메서드 안에서 보장한다.
- “controller에서 이미 검증했으니 domain에서는 생략”을 금지한다.
- domain rule 실패는 명시적 domain exception 또는 명시적 실패 모델로 표현한다.
- domain 객체에 web concern(BindingResult, @RequestBody, @ModelAttribute)를 들이지 않는다.

Bean Validation 애노테이션 사용 기준

Spring은 Bean Validation이 domain model 속성에도 선언될 수 있다고 설명하지만, 이 프로젝트에서는 domain의 핵심 규칙을 애노테이션만으로 표현하는 방식을 기본값으로 두지 않는다. Bean Validation은 보조 표현일 수 있으나, 최종 규칙 enforcement는 도메인 코드가 담당한다. 이는 Official + Practice + Project Recommendation 이다.

### 4.4 Infrastructure 레이어

프로젝트 규칙:

- 외부 API client, messaging adapter 등에서는 외부 계약에 필요한 검증을 할 수 있다.
- 다만 infrastructure validation은 외부 포맷/프로토콜/계약 확인용이지, 핵심 business rule의 본진이 아니다.
- 외부 요청 DTO와 내부 domain object 사이의 매핑 전에 필요한 최소 검증은 허용한다.
- domain 규칙을 infrastructure adapter에 중복 구현하지 않는다.

## 5. Bean Validation / Spring Validator 사용 규칙

### 5.1 @Valid / @Validated in controller

Spring MVC는 @Valid / @Validated로 command object 검증을 수행할 수 있고, method parameter constraint가 있으면 method validation이 개별 parameter validation을 대체한다. 또한 @Valid 자체는 constraint annotation이 아니며, @NotNull 같은 constraint와 함께 있을 때 method validation으로 이어질 수 있다.

프로젝트 규칙:

- request DTO 내부 필드 검증에는 @Valid
- validation group이 실제로 필요한 경우에만 @Validated
- scalar parameter 제약은 파라미터에 직접 constraint 부여
- controller 클래스 레벨 @Validated는 기본 금지

### 5.2 controller 클래스 레벨 @Validated 금지

Spring Framework 6.1+에서 MVC built-in method validation을 제대로 쓰려면 controller 클래스 레벨 @Validated를 제거해야 한다고 공식 문서가 명시한다. 클래스 레벨 @Validated를 두면 AOP proxy 방식이 적용된다.

프로젝트 규칙:

- controller 클래스에는 @Validated를 붙이지 않는다.
- 필요한 경우 메서드 파라미터에 직접 constraint를 선언해 MVC built-in method validation을 사용한다.

### 5.3 Spring Validator와 @InitBinder

Spring은 global validator를 MVC config로, local validator를 @InitBinder를 통해 controller 또는 @ControllerAdvice에 등록할 수 있다고 설명한다. @InitBinder는 binder 초기화, 변환, 포맷팅, controller-local customization에 쓰인다.

프로젝트 규칙:

- Bean Validation으로 충분하면 custom Spring Validator를 추가하지 않는다.
- 특정 web input에만 필요한 로컬 검증은 @InitBinder + custom Validator를 검토할 수 있다.
- @InitBinder는 web binding 전용 커스터마이징 지점으로 사용한다.
- application/domain business rule을 @InitBinder validator에 넣지 않는다.

### 5.4 BindingResult 사용 기준

Spring MVC는 Errors / BindingResult를 method parameter 바로 뒤에 두면 일부 validation error를 controller 안에서 직접 다룰 수 있다고 설명한다. 그러나 다른 파라미터에 validation error가 있으면 HandlerMethodValidationException이 발생할 수 있다.

프로젝트 규칙:

- 일반 REST API에서는 BindingResult를 광범위하게 사용하지 않는다.
- 기본 경로는 예외 발생 → @RestControllerAdvice에서 ApiResult 변환이다.
- HTML form 처리처럼 controller가 오류를 직접 조합해야 하는 경우에만 제한적으로 사용한다.

## 6. 이 프로젝트의 기본 배치

이 프로젝트의 기본 배치는 다음과 같다.

presentation:

- request DTO 구조 검증
- request param/path/header 기본 제약
- transport-level parsing/format validation

application:

- use case 전제조건
- 조회/상태 의존 정책
- 여러 입력의 조합 규칙

domain:

- value object/entity/aggregate invariant
- 생성/변경 가능 조건
- 핵심 business rule

infrastructure:

- 외부 시스템 계약 검증
- protocol/adapter 수준 포맷 검증

## 7. 금지 규칙

다음은 기본 금지다.

- filter / interceptor / resolver / advice 에 business validation 작성
- entity를 @RequestBody / @ModelAttribute로 직접 바인딩
- controller validation만 믿고 domain invariant 생략
- service method validation proxy에 business rule enforcement를 전부 위임
- ApiResult 에러 메시지 생성을 validator 안에서 직접 수행
- validation group을 명확한 이유 없이 남발
- 단순 request DTO 검증까지 repository 조회 기반 custom validator로 만드는 것

## 8. 체크리스트

다음 질문으로 위치를 결정한다.

- HTTP 입력 형식, nullability, 길이, 범위, 단순 필드 검증인가? presentation
- 여러 입력 조합, 현재 상태, 조회 결과에 따라 use case 수행 가능 여부를 판단하는가? application
- 이 객체가 존재하거나 이 행위를 수행할 수 있는지의 핵심 규칙인가? domain
- 외부 API 계약이나 adapter 포맷 문제인가? infrastructure
- 특정 request binding 방식, formatter, web-only validator가 필요한가? @InitBinder / local validator
