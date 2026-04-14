# value object 기준

## 목적

Value Object는 **식별자보다 값 자체가 본질인 도메인 개념**을 표현한다.  
문자열, 숫자, primitive 조합으로 흩뿌려진 의미를 작은 타입으로 끌어올려,
- 의미를 드러내고
- 불변식을 한 곳에 모으고
- 잘못된 조합을 줄이는 것이 목적이다.

## 공식 의미

- Value Object는 개념적 identity가 없다.
- Value Object는 생성 후 immutable하게 다루는 것이 기본이다.
- 값이 같으면 서로 interchangeable 하다.
- 값 기반 객체는 identity-sensitive 연산(`==`, identity hash, synchronization)에 의존하지 않는다.
- `equals` / `hashCode`는 identity가 아니라 상태값 기준이어야 한다.

## 기본 규칙

### 1. identity가 아니라 값이 본질이면 Value Object를 우선 검토
다음은 Value Object 후보다.

- 이메일
- 사용자 이름
- 금액
- 통화
- 기간
- 주소
- 토큰 문자열
- 공개키 식별자
- provider code
- 정규화된 path/host/url 일부
- 비즈니스 규칙이 붙은 ID wrapper

질문:
- “무엇인가”보다 “어떤 값인가”가 본질인가?
- 같은 값이면 같은 것으로 취급해야 하는가?
- 생성 시점에 검증/정규화 규칙을 묶고 싶은가?

### 2. Value Object는 기본적으로 immutable
Value Object는 생성 후 상태가 바뀌지 않게 설계한다.

기본:
- final field
- setter 없음
- 변경이 필요하면 새 인스턴스 반환

변경 가능한 컬렉션/객체를 내부에 들고 있으면 defensive copy 또는 immutable snapshot을 사용한다.

### 3. equality는 값 기준
Value Object의 동등성은 값으로 판단한다.

기본:
- `equals` / `hashCode` 구현
- record를 쓸 수 있으면 record 우선 검토
- `==` 비교 금지
- identity-based lock/synchronization 금지

### 4. 생성 시점에 불변식을 강제
Value Object는 가능한 한 생성 시점에 유효한 상태만 허용한다.

예:
- `UserEmail.from(...)` 에서 trim/lowercase/형식 검증
- `Money.of(...)` 에서 음수 금지/scale 정리
- `UserName.from(...)` 에서 길이/문자 규칙 검증

“일단 넣고 나중에 확인”을 금지한다.

### 5. primitive obsession을 줄이는 방향으로 도입
다음과 같은 경우 Value Object 도입을 우선 검토한다.

- 같은 `String`이지만 의미가 여러 개라 실수 가능성이 큼
- 여러 곳에서 같은 검증/정규화가 반복됨
- 메서드 시그니처에서 의미가 안 드러남
- 잘못된 값 조합을 타입 수준에서 줄이고 싶음

### 6. 너무 사소한 래퍼는 만들지 않는다
다음은 도입을 보류할 수 있다.

- 검증/정규화/행위가 전혀 없음
- 의미가 너무 자명하고 혼동 위험이 낮음
- 래퍼 비용이 실제 이득보다 큼

즉 모든 primitive를 기계적으로 감싸지 않는다.

### 7. Value Object는 도메인 언어를 사용
이름은 기술 표현이 아니라 business meaning을 드러내야 한다.

좋은 방향:
- `UserEmail`
- `Money`
- `AuthProviderCode`
- `DisplayName`
- `TokenTtl`

지양:
- `StringWrapper`
- `ValueHolder`
- `CommonValue`

### 8. Value Object는 nullable 대신 명시적 의미를 우선
가능하면 Value Object 자체는 non-null로 다룬다.

부재 표현이 필요하면:
- Optional 반환
- nullable boundary 입력
- 별도 상태 타입
- empty/unknown 값을 실제 business state로 둘지 신중히 검토

null을 Value Object 의미의 일부처럼 쓰지 않는다.

### 9. Value Object는 DTO/Entity와 분리
Value Object는 domain 의미 타입이다.

기본:
- request DTO field를 그대로 Value Object로 바인딩하지 않음
- entity field를 그대로 Value Object로 대체하지 않고 매핑 전략을 명시
- DTO <-> domain, entity <-> domain 변환에서 Value Object를 생성/복원

### 10. 컬렉션을 포함하는 Value Object는 특히 신중
컬렉션이 들어가는 Value Object는 아래를 만족해야 한다.

- 컬렉션 자체가 immutable/unmodifiable
- 원소도 가능한 한 immutable
- equality/hashCode 의미가 분명함
- 순서 중요 여부가 명확함

### 11. 행위가 있어도 된다. 단, 값 의미와 관련된 행위여야 한다
Value Object는 단순 data carrier일 필요는 없다.

허용 예:
- 정규화
- 포맷 변환
- 비교
- 계산
- 조합
- 규칙 기반 convenience method

금지 예:
- repository 호출
- 외부 API 호출
- 전역 상태 의존
- 객체 그래프 조립의 중심이 되는 orchestration

### 12. persistence는 domain 의미를 우선하되 별도 매핑으로 해결
JPA entity는 persistence 제약을 받으므로 Value Object와 1:1로 같아야 할 필요는 없다.

기본:
- entity <-> domain mapper에서 Value Object 생성/복원
- 가능하면 embeddable/owned type 등 적절한 persistence 모델 사용 검토
- persistence 편의 때문에 domain Value Object를 포기하지 않음

### 13. record는 좋은 기본 선택지일 수 있다
Java record는 값 중심 타입 표현에 잘 맞을 수 있다.
단, 아래를 만족할 때 사용한다.

- 불변 구조가 자연스럽다
- 값 기반 equality가 맞다
- 생성 시 검증/정규화를 canonical constructor/factory로 명확히 표현할 수 있다

단, record를 쓴다고 자동으로 좋은 Value Object가 되는 것은 아니다.

### 14. Value Object는 작은 타입이지만 경계 비용을 줄여야 한다
도입 후 얻는 이득:
- 의미가 타입에 드러남
- 검증 중복 감소
- 잘못된 조합 감소
- 테스트 용이성 증가

단, 의미 없는 래퍼 남발은 금지한다.

## 프로젝트 기준 요약

- identity보다 값이 본질이면 Value Object 우선 검토
- 기본은 immutable
- equality는 값 기준
- 생성 시점에 불변식 강제
- primitive obsession 줄이기
- 너무 사소한 래퍼는 지양
- DTO/Entity와 분리
- persistence는 mapper/별도 매핑 전략으로 해결
- record는 좋은 선택지일 수 있으나 자동 정답은 아님
