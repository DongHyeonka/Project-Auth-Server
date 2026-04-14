# mapper separation 기준

## 목적

매퍼는 **한 모델의 데이터를 다른 모델로 번역하는 역할**만 맡는다.  
비즈니스 규칙, 영속성 접근, 외부 호출, 부수효과를 매퍼에 섞지 않는다.

## 공식 의미

- DTO는 프로세스/경계 사이에서 데이터를 옮기는 객체다.
- DTO와 domain object 사이를 옮기는 assembler/mapper를 둘 수 있다.
- Spring DataBinder는 입력 바인딩이 강력하지만, 바인딩 대상 설계를 신중히 해야 하며 보안상 주의가 필요하다.
- MapStruct 같은 매퍼 도구는 type-safe bean mapping 코드를 생성한다.
- 기존 target 객체 갱신은 `@MappingTarget` 같은 명시적 update mapping으로 표현할 수 있다.

## 기본 규칙

### 1. 매퍼는 “번역”만 한다
매퍼의 책임은 아래 중 하나다.

- request DTO -> command
- domain -> response DTO
- persistence entity -> domain
- domain -> persistence entity
- external DTO -> internal model
- internal model -> external DTO

즉 “형태를 바꾸는 일”까지만 한다.

### 2. 비즈니스 규칙은 매퍼에 넣지 않는다
다음은 매퍼 책임이 아니다.

- 상태 전이 결정
- 권한 판정
- 에러 코드 결정
- 정책 선택
- 유효성 최종 판정
- 도메인 invariant 강제의 주 책임

단, 단순한 정규화/포맷 수준의 보조 변환은 허용될 수 있다.

### 3. 매퍼에서 repository / external API / service 호출 금지
매퍼는 pure mapping에 가깝게 유지한다.

금지:
- DB 조회
- 외부 API 호출
- 다른 aggregate 탐색을 위한 repository 호출
- 메시지 발행
- 파일/네트워크 접근

매핑에 필요한 부가 데이터가 있으면 호출자가 먼저 준비해서 매퍼에 전달한다.

### 4. 경계마다 매퍼를 분리한다
다음 경계를 하나의 매퍼로 섞지 않는다.

- web request/response 변환
- persistence entity 변환
- external integration 변환

예:
- `UserWebMapper`
- `UserPersistenceMapper`
- `VaultApiMapper`

처럼 경계별로 분리한다.

### 5. DTO / Domain / Entity를 직접 섞지 않는다
하나의 매퍼가 다음을 동시에 다루며 의미를 섞지 않게 한다.

- request DTO
- domain
- JPA entity
- external API payload

필요하면 경계별로 mapper를 여러 개 둔다.

### 6. 매퍼는 기술 세부보다 구조적 대응 관계를 표현
좋은 매퍼는 아래를 명확하게 보여야 한다.

- 어떤 source를 어떤 target으로 바꾸는가
- 어떤 필드가 대응되는가
- 어떤 값이 누락되거나 기본값 처리되는가

반대로 아래는 매퍼에 새기지 않는다.

- HTTP 상태 코드
- DB 트랜잭션
- serialization 포맷 정책
- retry/fallback 정책

### 7. update mapping은 명시적으로만
기존 target을 수정하는 매핑은 “새로 생성하는 매핑”과 구분한다.

기본:
- create mapping
- update mapping

을 별도 메서드로 둔다.

기존 객체 갱신은 side effect가 있으므로 이름과 계약을 분명히 한다.

### 8. null / empty / default 처리 정책을 숨기지 않는다
매퍼는 다음을 명확히 해야 한다.

- null source를 허용하는가
- null field를 무시하는가
- null이면 target을 덮어쓰는가
- empty collection을 그대로 넣는가
- 기본값을 넣는가

정책이 중요하면 호출자/standard 문서에서 먼저 정하고 매퍼에 일관되게 반영한다.

### 9. mapper는 가능한 한 결정적이고 테스트 가능해야 한다
같은 입력이면 같은 결과가 나와야 한다.

지양:
- 현재 시각 생성
- 랜덤 값 생성
- 환경값 조회
- thread-local/MDC 접근

정말 필요하면 호출자가 값을 주입한다.

### 10. request binding과 도메인 생성은 구분한다
Spring DataBinder/Web binding이 request를 객체로 바꿔 주더라도,
그 객체를 domain으로 승격하는 과정은 별도 매퍼/assembler 또는 factory에서 통제한다.

즉:
- web binding = 입력 수집
- mapper = 구조 변환
- domain factory/value object = 의미/불변식 부여

### 11. persistence mapper는 DB nullable/오염 상태를 domain으로 직접 흘리지 않는다
entity -> domain 매핑에서는:
- nullable column
- legacy 값
- 잘못된 저장 데이터
를 명시적으로 처리한다.

필요하면 예외를 던지거나 복원 규칙을 적용하지만, 조용히 의미를 바꾸지 않는다.

### 12. external integration mapper는 wire format을 코어에 새기지 않는다
외부 API JSON/XML/HTTP payload 구조는 integration 전용 mapper에서 소화한다.
core/application/domain은 외부 wire format 세부를 몰라야 한다.

### 13. 자동 매핑 도구를 써도 책임은 그대로
MapStruct 같은 도구를 써도 아래 원칙은 변하지 않는다.

- 비즈니스 로직을 매퍼에 넣지 않는다
- update/create를 구분한다
- 경계별 매퍼를 분리한다
- null/default 정책을 숨기지 않는다

도구는 구현 보조일 뿐 설계 기준을 대체하지 않는다.

## 프로젝트 기준 요약

- 매퍼는 번역만 한다
- 비즈니스 정책/DB 조회/외부 호출 금지
- web / persistence / integration 매퍼 분리
- create mapping과 update mapping 분리
- null/default 정책 명시
- 결정적이고 테스트 가능하게 유지
- domain 의미 부여와 매핑을 혼동하지 않음
