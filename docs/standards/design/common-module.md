# common module 허용 기준

## 목적

`common` 모듈은 공유 편의 때문에 만드는 기본 모듈이 아니다.  
기본값은 **만들지 않음** 이다.

이 문서의 목적은:
- “일단 common으로 보내자”를 막고
- 모듈 경계를 흐리는 공유를 줄이고
- 정말 필요한 공용 코드만 예외적으로 허용하는 것이다

## 공식/원전 기준 요약

- 모듈은 외부에 노출하는 API와 내부 구현을 구분해야 한다.
- 다른 모듈은 공개된 API에만 의존해야 하며, 내부 구현 의존은 막아야 한다.
- 헥사고날 아키텍처의 핵심도 경계와 역할 분리다.
- 겉보기 중복만 보고 조기 추상화를 만들면 이후 변경 비용이 커질 수 있다.

## 기본 규칙

### 1. `common`은 기본 금지
새 코드를 추가할 때 기본 선택지는 아래 순서다.

1. owning layer/module 내부에 둔다
2. 경계가 필요하면 해당 module의 API로 노출한다
3. 그래도 어느 곳에도 자연스럽게 속하지 않을 때만 `common`을 검토한다

즉 “어디 둘지 애매하면 common”을 금지한다.

### 2. `common`은 아래 조건을 모두 만족할 때만 허용
다음이 모두 참일 때만 `common` 도입을 검토한다.

- 3개 이상 모듈에서 실제로 재사용된다
- 변화 이유가 같다
- 특정 모듈이 소유한다고 보기 어렵다
- `common`으로 옮겨도 의존 방향이 더 나빠지지 않는다
- 모듈 API를 좁게 노출하는 방식으로 해결하기 어렵다

하나라도 애매하면 기존 owning module에 둔다.

### 3. “중복 존재”만으로는 common 근거가 아니다
코드가 비슷해 보여도 다음이면 `common`으로 보내지 않는다.

- 서로 다른 비즈니스 문맥에서 독립적으로 변할 가능성이 큼
- 지금은 비슷하지만 미래 요구가 갈라질 가능성이 큼
- common으로 모으면 이름과 책임이 모호해짐

즉 진짜 공통 원인(common cause)일 때만 허용한다.

### 4. 레이어 경계를 깨는 공유 금지
`common`이 아래를 섞는 것을 금지한다.

- presentation + domain
- domain + infrastructure
- application + web transport
- persistence 모델 + API 모델
- 외부 API payload + 내부 domain meaning

공유보다 경계 보존이 우선이다.

### 5. `common`에는 business policy를 두지 않는다
다음은 `common`에 두지 않는다.

- 도메인 규칙
- 상태 전이 규칙
- 권한 판정
- 에러 코드 정책
- 외부 연동별 특화 규칙

이런 것은 반드시 owning module 또는 boundary contract가 소유한다.

### 6. `common`에 둘 수 있는 것
예외적으로 허용 가능한 후보:

- 경량 value type
- 순수하고 작은 utility
- 여러 모듈이 같은 이유로 쓰는 매우 안정적인 helper
- 모듈 경계를 깨지 않는 공통 annotation / marker / tiny abstraction
- 명확한 소유자가 없는 순수한 language-level helper

단, 이것도 실제 재사용과 변화 이유가 검증되어야 한다.

### 7. `common`의 utility는 특히 좁게 제한
utility는 아래 조건을 만족할 때만 허용한다.

- side effect 없음
- framework/business/persistence 의존 없음
- 이름만 보고 역할이 분명함
- 단순 문자열/시간/컬렉션 helper라도 owning type 안에 둘 수 없는 이유가 있음

`StringUtils`, `DateUtils`, `CommonUtils`, `AppUtils` 같은 잡동사니 묶음은 금지한다.

### 8. 모듈 API 노출이 common보다 우선
Spring Modulith의 방향처럼, 공용화가 필요해 보일 때 먼저 검토할 것은:

- 해당 모듈의 공개 API로 노출할 수 있는가
- named interface처럼 노출 범위를 좁게 지정할 수 있는가
- explicit dependency로 필요한 부분만 허용할 수 있는가

즉 “common으로 이동”보다 “모듈 API 설계 개선”을 먼저 본다.

### 9. 공용 타입은 더 보수적으로 관리
`common`으로 이동한 타입은 사실상 여러 모듈이 기대는 기반이 된다.

기본:
- 변경에 더 보수적이어야 한다
- naming을 더 명확히 해야 한다
- Javadoc/문서가 더 중요하다
- examples/tests가 함께 있어야 한다

### 10. `common`은 dump zone이 아니다
다음 징후가 보이면 잘못된 `common`이다.

- 이름이 `Common*`, `Util*`, `Base*`, `Helper*` 위주
- business/domain/web/persistence 코드가 섞여 있음
- 모듈 간 순환 의존을 가리기 위해 common을 사용
- “일단 여기 두자”가 반복됨

이 경우 common을 늘리는 대신 다시 소유 모듈로 분해한다.

### 11. common 도입은 문서화한다
새로운 `common` 타입/패키지를 추가할 때는 최소한 아래를 설명한다.

- 왜 owning module에 둘 수 없는가
- 어떤 모듈들이 실제로 재사용하는가
- 같은 이유로 어떻게 함께 바뀌는가
- 어떤 경계를 깨지 않는가

설명할 수 없으면 common으로 보내지 않는다.

## 프로젝트 기준 요약

- `common` 기본 금지
- 재사용만으로는 부족하고 “같은 이유로 함께 변함”이 필요
- 모듈 API 설계 개선이 common보다 우선
- 경계/레이어를 섞는 공용화 금지
- business policy는 common 금지
- utility/common dump zone 금지
- 예외적 허용 시에도 좁고 순수하고 안정적인 타입만