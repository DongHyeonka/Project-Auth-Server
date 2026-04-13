# @ConfigurationProperties 사용 기준

## 목적

외부 설정은 산발적인 문자열 주입이 아니라, **의미 있는 설정 객체**로 묶어 관리한다.  
설정은 business object가 아니라 **configuration contract** 로 취급한다.

## 공식 의미

- `@ConfigurationProperties`는 externalized configuration을 타입 안전하게 바인딩하기 위한 애노테이션이다.
- 클래스 또는 `@Configuration` 안의 `@Bean` 메서드에 붙일 수 있다.
- 바인딩은 setter 또는 생성자 인자를 통해 수행될 수 있다.
- `@EnableConfigurationProperties` 또는 `@ConfigurationPropertiesScan`으로 등록할 수 있다.
- `@ConfigurationProperties`는 `@Value`보다 relaxed binding, metadata 지원에 유리하다.
- `@ConfigurationProperties`는 SpEL을 평가하지 않는다.
- `@ConfigurationPropertiesScan`은 `@Component`가 붙은 클래스를 스캔 대상으로 잡지 않는다.
- `@Validated`로 properties validation을 수행할 수 있다.
- `Optional`은 `@ConfigurationProperties`에서 권장되지 않는다.

## 기본 규칙

### 1. 의미 있는 설정 그룹은 `@ConfigurationProperties` 우선
다음은 `@ConfigurationProperties`를 우선 검토한다.

- 같은 prefix 아래 여러 설정값이 함께 움직임
- 계층형/nested 설정이 있음
- 설정 검증이 중요함
- 여러 bean이 같은 설정 집합을 참조함
- 운영 문서와 IDE metadata 지원이 중요함

예:
- Vault 설정
- OAuth client 설정
- JWT 설정
- scheduler/retry 설정
- feature toggle 묶음

### 2. 단발성 한두 값만 필요하면 `@Value`를 제한적으로 허용
다음은 `@Value`를 허용할 수 있다.

- 단일 상수성 설정값
- 로컬 config class 안에서만 쓰는 매우 작은 값
- SpEL이 실제로 필요한 경우

단, 애플리케이션 자체 설정 키 집합이라면 `@ConfigurationProperties`를 우선한다.

### 3. properties class는 설정 계약만 표현
`@ConfigurationProperties` 클래스의 책임은:
- 설정값 구조 표현
- 타입 안전 바인딩
- 검증
- 합리적 기본값 표현

다음은 넣지 않는다.
- business logic
- 외부 API 호출
- repository/service 호출
- 큰 계산 로직
- runtime mutable state

### 4. prefix는 명확하고 안정적으로 설계
prefix는 기능/도메인 경계를 드러내야 한다.

좋은 방향:
- `auth.jwt`
- `auth.oauth.google`
- `vault.transit`
- `app.retry`

지양:
- `config`
- `common`
- `misc`
- 의미가 너무 넓은 prefix

### 5. 등록 방식은 스캔과 명시 등록을 구분
기본 선택:
- 애플리케이션 내부 일반 설정 타입 -> `@ConfigurationPropertiesScan`
- 조건부 등록/auto-configuration/명시적 wiring 필요 -> `@EnableConfigurationProperties` 또는 `@Bean` + `@ConfigurationProperties`

### 6. `@Component`와 `@ConfigurationProperties`를 습관적으로 같이 쓰지 않는다
properties class는 설정 바인딩 타입이지 일반 component가 아니다.

기본:
- scanning 대상 properties -> `@ConfigurationPropertiesScan`
- 명시 등록이 필요하면 `@EnableConfigurationProperties`

`@Component`를 붙여 일반 bean처럼 다루는 패턴은 지양한다.

### 7. 가능한 한 immutable 구조를 선호
설정은 보통 startup 후 바뀌지 않는 계약이다.

기본 방향:
- 생성자 기반 바인딩 또는 immutable한 구조 선호
- 변경 가능한 setter-only bag object를 기본값으로 삼지 않음
- 필수 설정은 생성 시점에 확정되게 설계

### 8. `Optional` 필드 금지
Spring Boot 공식 문서상 `Optional`은 `@ConfigurationProperties`에서 권장되지 않는다.

기본:
- nullable field
- 기본값
- nested object
- 명시적 default object
중 하나로 표현한다.

### 9. validation은 startup에서 최대한 실패하게
설정이 잘못되면 런타임 깊은 지점에서 터지지 않게, 바인딩 시점 검증을 우선한다.

기본:
- `@Validated`
- Bean Validation annotation (`@NotNull`, `@Min`, `@Pattern` 등)
- nested properties는 필요 시 `@Valid`

### 10. 기본값 정책을 숨기지 않는다
기본값은 다음 중 하나로 명시한다.

- 필드 기본값
- 생성자 기본값
- 명시적 nested default object
- 문서화된 운영 기본값

“값이 없으면 나중에 어딘가에서 처리”를 금지한다.

### 11. Environment 직접 조회보다 properties bean 주입 우선
application/service/infrastructure 코드에서 `Environment#getProperty(...)`를 흩뿌리지 않는다.

기본:
- 관련 설정은 properties 객체로 묶고
- 필요한 bean에 주입한다

예외:
- truly dynamic property lookup
- framework/bootstrap 초기화 특수 상황

### 12. 설정 객체는 소유 모듈 가까이에 둔다
properties class는 그것을 사용하는 기능/모듈 옆에 둔다.

예:
- `vault` 설정은 vault adapter/config 근처
- `jwt` 설정은 jwt/token 모듈 근처

`CommonProperties`, `AppProperties`처럼 전역 잡동사니 설정 객체는 지양한다.

### 13. third-party bean 바인딩도 가능하지만, 범위를 제한
외부 라이브러리 객체를 `@Bean` 메서드 + `@ConfigurationProperties`로 바인딩할 수 있다.
단, 그 경우도:
- 명확한 prefix
- 명확한 config class
- 외부 라이브러리 설정 범위 제한
을 지킨다.

### 14. 설정 계약과 business meaning을 혼동하지 않는다
예:
- `token-expiration-seconds`는 설정 계약
- `TokenTtl`은 도메인 의미일 수 있다

필요하면 properties -> domain config/value object 변환 단계를 둔다.
설정 타입을 그대로 domain everywhere에 흘려보내지 않는다.

### 15. 문서와 metadata를 함께 고려
`@ConfigurationProperties`의 장점 중 하나는 metadata/IDE 지원이다.
애플리케이션이 제공하는 설정 키는:
- prefix 일관성
- 이름 일관성
- 설명/문서화
를 함께 고려한다.

## 프로젝트 기준 요약

- 의미 있는 설정 집합은 `@ConfigurationProperties` 우선
- 한두 개 단발성 값만 `@Value` 제한 허용
- properties class는 설정 계약만 표현
- `@Component`와 습관적 결합 금지
- immutable 구조 선호
- `Optional` 필드 금지
- validation은 startup에서 최대한 실패하게
- `Environment` 직접 조회보다 properties bean 주입 우선
- 설정 객체는 owning module 가까이에 둔다
