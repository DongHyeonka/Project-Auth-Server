# DTO / Domain / Entity separation 기준

## 목적

DTO, Domain, Entity는 이름만 다른 비슷한 객체가 아니라 **서로 다른 경계와 책임을 가진 모델**이다.  
형태가 비슷하더라도 역할이 다르면 분리한다.

## 공식 의미

- DTO는 프로세스/경계 사이에서 데이터를 운반하는 객체다.
- Entity는 persistence provider가 관리하는 영속 모델이다.
- Spring request binding은 강력하지만, 외부 입력을 객체 그래프에 직접 바인딩하는 것은 보안상 주의가 필요하다.
- 따라서 web 입력 모델, 비즈니스 모델, persistence 모델을 하나로 섞지 않는 것이 안전하다.

## 기본 규칙

### 1. DTO는 경계 데이터 모델이다
DTO는 다음 경계에서만 사용한다.

- HTTP request / response
- external API request / response
- message payload
- batch/file I/O payload

DTO의 책임:
- 데이터 운반
- 직렬화/역직렬화 경계 표현
- validation annotation 수용 가능
- API 계약 표현

DTO의 책임이 아닌 것:
- 비즈니스 규칙
- 도메인 불변식 유지
- 영속성 상태 관리
- repository/service 호출

### 2. Domain은 비즈니스 의미 모델이다
Domain은 다음을 표현한다.

- entity
- value object
- domain service
- domain policy
- 불변식
- 상태 전이 의미

Domain의 책임:
- 비즈니스 의미
- 유효한 상태 보장
- 행위와 규칙 표현

Domain의 책임이 아닌 것:
- JSON 구조
- HTTP 요청/응답 형식
- JPA annotation/persistence lifecycle
- 외부 API payload 구조

### 3. Entity는 persistence 모델이다
Entity는 DB와 persistence provider가 다루는 모델이다.

Entity의 책임:
- 테이블/컬럼/관계 매핑
- persistence lifecycle 대응
- 저장 가능한 상태 표현

Entity의 책임이 아닌 것:
- API 응답 계약
- 비즈니스 정책 결정
- 외부 연동 payload 표현

### 4. 하나의 타입으로 세 역할을 겸하지 않는다
다음은 기본 금지한다.

- request DTO를 그대로 domain으로 사용
- JPA entity를 그대로 response DTO로 반환
- domain object에 JPA/JSON/validation annotation을 한꺼번에 섞기

형태가 비슷해도 책임이 다르면 타입을 분리한다.

### 5. 경계마다 변환을 명시한다
기본 흐름:
- request DTO -> command / domain input
- entity -> domain
- domain -> response DTO
- external payload -> internal model

변환은 mapper/assembler/factory 같은 명시적 경계에서 수행한다.

### 6. DTO에는 validation을 둘 수 있지만, 최종 의미 검증은 domain이 맡는다
DTO에 둘 수 있는 것:
- `@NotBlank`
- `@Size`
- 형식 검증
- 웹 입력 범위 검증

하지만 다음은 domain 책임이다.
- 불변식
- 상태 전이 타당성
- 정책 기반 허용 여부
- aggregate 일관성

즉 DTO validation이 domain validation을 대체하지 않는다.

### 7. Entity의 nullable/관계/지연로딩 특성을 domain에 그대로 전파하지 않는다
DB 제약 때문에 entity가 nullable/관계 중심일 수는 있다.  
하지만 domain은 비즈니스 invariant 기준으로 더 엄격할 수 있다.

기본:
- entity -> domain 변환 시 검증/정규화
- domain은 persistence 편의보다 비즈니스 의미 우선

### 8. Response DTO는 domain을 그대로 노출하지 않는다
response는 외부 계약이다.  
기본적으로 domain object를 그대로 JSON으로 노출하지 않는다.

이유:
- 내부 구조 변경이 외부 계약에 새어 나간다
- 민감정보/불필요 필드 노출 위험
- 직렬화 shape가 domain 설계를 오염시킨다

### 9. Request DTO를 entity에 직접 바인딩하지 않는다
Spring DataBinder/WebDataBinder는 강력하지만 보안상 주의가 필요하다.  
따라서 외부 입력을 entity나 깊은 도메인 객체에 직접 바인딩하지 않는다.

기본:
- request DTO에만 바인딩
- 이후 명시적 변환을 거쳐 command/domain으로 이동

### 10. Entity를 domain과 1:1로 맞추려 하지 않는다
entity와 domain은 비슷할 수 있지만 항상 같아야 할 필요는 없다.

예:
- entity는 FK/nullable/지연 로딩 중심
- domain은 value object/invariant/행위 중심

“필드가 같아 보이니 하나로 합친다”를 금지한다.

### 11. DTO는 record/단순 데이터 구조를 우선 검토
DTO는 경계 데이터 운반이 목적이므로, 불변/단순 구조를 우선 검토한다.
단, framework binding/serialization 요구사항이 있으면 그 제약을 따른다.

### 12. Domain은 DTO naming을 따라가지 않는다
도메인 타입 이름은 API 필드명/JSON 필드명보다 비즈니스 의미를 우선한다.

금지 예:
- `UserResponseName`
- `ProviderRequestCode`

도메인은 business language를 사용한다.

### 13. Entity는 persistence 편의 메서드를 가질 수 있지만 도메인 규칙 중심 타입이 되지 않게 한다
entity에 persistence 편의 메서드가 있을 수는 있다.  
하지만 핵심 비즈니스 규칙을 entity/JPA lifecycle에 과도하게 묶지 않는다.

### 14. Mapping 비용보다 경계 명확성이 더 중요하다
DTO/Domain/Entity 분리는 변환 코드가 늘 수 있다.  
그러나 그 비용보다:
- 경계 명확성
- 보안성
- 변경 범위 제한
- 직렬화/영속성 오염 방지
의 이익이 더 크다.

## 프로젝트 기준 요약

- DTO = 경계 데이터
- Domain = 비즈니스 의미
- Entity = persistence 모델
- 하나의 타입으로 세 역할 겸용 금지
- request는 DTO에만 바인딩
- DTO validation과 domain invariant를 분리
- entity nullable/관계를 domain에 그대로 전파하지 않음
- response DTO로 외부 계약을 명시
