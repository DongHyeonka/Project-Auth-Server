# enum / constants 기준

## 목적

고정된 의미 집합은 문자열/정수 상수 남발 대신 enum으로 표현한다.  
상수는 단순히 `static final`인 값이 아니라, 실제로 변경 불가능하고 의미가 분명한 값으로 관리한다.

## 공식 의미

- enum은 미리 정의된 상수 집합을 표현하는 특별한 클래스다.
- 각 enum constant는 해당 enum 타입의 고유 인스턴스다.
- enum은 필드, 메서드, 생성자를 가질 수 있다.
- `ordinal()`은 대부분의 개발자가 사용할 용도가 아니며 `EnumSet`, `EnumMap` 같은 저수준 구조를 위한 값이다.
- `name()`은 선언된 정확한 이름을 반환한다.
- `toString()`은 더 사람이 읽기 좋은 문자열이 필요할 때만 재정의한다.
- enum key/set에는 `EnumMap`, `EnumSet` 같은 특화 구현을 우선 검토한다.

## 기본 규칙

### 1. 고정된 값 집합이면 enum 우선
다음과 같은 경우는 enum을 우선 검토한다.

- 상태
- provider 종류
- 역할 종류
- 모드
- 정책 선택지
- 결과 코드의 제한된 집합

금지 예:
- `"LOCAL"`, `"GOOGLE"`, `"KAKAO"` 같은 문자열 분기
- `1`, `2`, `3` 같은 매직 넘버 상태값 분기

### 2. 값 집합이 열려 있으면 enum을 쓰지 않는다
외부 시스템이 임의 값을 추가할 수 있거나, 런타임에 확장 가능한 값이면 enum보다 다른 모델을 검토한다.

예:
- 자유 입력 태그
- 외부 설정으로 계속 늘어나는 코드값
- tenant별 커스텀 상태

### 3. `ordinal()` 의존 금지
다음 용도로 `ordinal()`을 사용하지 않는다.

- DB 저장값
- 외부 API/JSON 값
- 비즈니스 분기
- 배열 인덱스 계약
- stable code/wire number

안정적인 숫자/문자 코드가 필요하면 enum 필드로 명시한다.

### 4. `name()`은 내부 안정 식별자, 사용자 표시값은 별도 필드/메서드
- 내부 고정 식별이 필요하면 `name()`
- 사용자에게 보이는 문자열은 별도 필드나 메서드
- API/DB/wire 값도 별도 필드로 분리

즉:
- internal name
- display label
- external code
를 섞지 않는다.

### 5. `toString()`은 신중하게
공식 문서상 `toString()`은 필요하면 더 programmer-friendly 한 문자열로 재정의할 수 있다.  
하지만 프로젝트에서는 다음 기준을 따른다.

- 로깅/디버깅/개발자용 표현만 바꿀 때 제한적으로 허용
- 외부 계약, JSON, DB 저장값을 `toString()`에 의존하지 않는다
- 외부 계약값은 명시적 getter/field를 사용한다

### 6. enum 비교는 enum끼리 직접 비교
동일 enum 타입 비교는 enum 값 자체로 직접 비교한다.  
비즈니스 의미 비교를 위해 문자열로 변환해서 비교하지 않는다.

금지 예:
- `status.name().equals("ACTIVE")`
- `provider.toString().equals(input)`

### 7. enum set/map은 `EnumSet` / `EnumMap` 우선 검토
enum을 key/set element로 사용할 때는 다음을 우선 검토한다.

- 집합 -> `EnumSet`
- 맵 -> `EnumMap`

비트 플래그용 `int`나 일반 `HashSet`/`HashMap`을 습관적으로 쓰지 않는다.

### 8. switch/분기는 enum 의미 중심으로 작성
enum 분기는 문자열/정수 코드가 아니라 enum 값 자체를 기준으로 작성한다.

또한:
- default로 조용히 삼키지 않는다
- 새로운 enum 값 추가 시 분기 누락이 드러나도록 작성한다

### 9. 상수는 진짜 immutable일 때만 상수로 취급
상수는 다음을 만족해야 한다.

- `static final`
- 값 자체가 immutable
- 내부 원소도 변경으로 의미가 흔들리지 않음

즉:
- mutable collection
- mutable object reference
- 변경 가능한 배열
은 상수처럼 다루지 않는다.

### 10. 관련 상수는 “상수 클래스”보다 소유 타입 근처에 둔다
다음 순서를 우선한다.

1. enum으로 승격 가능한 값이면 enum
2. 특정 클래스 책임이면 해당 클래스 내부 상수
3. 여러 모듈에 걸친 진짜 전역 상수만 별도 위치

의미 없는 `Constants`, `CommonConstants`, `AppConstants` 집합소는 기본 금지다.

### 11. null enum 기본 금지
enum 값은 가능한 한 non-null로 다룬다.
부재 표현이 필요하면:
- Optional 반환
- nullable boundary 입력
- 명시적 `UNKNOWN` / `UNSPECIFIED` 도입 여부를 신중히 검토

단, `UNKNOWN`이 실제 비즈니스 상태가 아닐 경우 null 회피용으로 남용하지 않는다.

### 12. persistence / serialization 계약은 별도 기준을 따른다
enum을 DB나 JSON에 노출할 때는 language 차원 기본 동작에 기대지 않고,
- 어떤 값을 저장/전송할지
- 호환성을 어떻게 유지할지
를 명시적 기준으로 정의한다.

## 프로젝트 기준 요약

- 고정된 의미 집합은 enum 우선
- 열린 값 집합은 enum 지양
- `ordinal()` 의존 금지
- internal name / display label / external code 분리
- enum set/map은 `EnumSet` / `EnumMap` 우선
- mutable 값은 상수처럼 다루지 않음
- 잡다한 constants class 기본 금지
