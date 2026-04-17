# Column Types 기준

## 1. 목적

이 문서는 PostgreSQL 컬럼 타입을 어떤 기준으로 선택할지 정의한다.

이 문서의 목표는 다음과 같다.

- 데이터 의미에 맞는 타입을 고른다
- 애매한 범용 타입 남용을 줄인다
- JPA/Hibernate 매핑과 PostgreSQL 네이티브 타입을 가능한 한 잘 맞춘다
- 정확도, 시간대, 검색성, 인덱싱 특성을 고려한 기본값을 만든다

## 2. 근거 수준

- Official: PostgreSQL / Hibernate 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 타입 특성 위에 일반적인 실무 기준을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 컬럼 타입은 “저장 가능”이 아니라 “의미 표현”으로 고른다

PostgreSQL은 다양한 내장 타입을 제공하고, 각 타입은 정확도, 정렬, 비교, 저장 형식, 인덱싱 특성이 다릅니다. 따라서 타입 선택은 “일단 들어가기만 하면 된다”가 아니라 이 값이 무엇을 의미하는가를 기준으로 해야 합니다.

프로젝트 규칙:

- 문자열처럼 보여도 사실상 수치면 numeric/integer 계열을 먼저 검토한다
- 시간처럼 보여도 실제 의미가 시점인지, 날짜인지, 로컬 시각인지 구분한다
- JSON처럼 유연한 타입은 정말 반정형 데이터일 때만 쓴다

### 3.2 PostgreSQL 네이티브 타입을 우선 사용한다

PostgreSQL은 uuid, jsonb, numeric, timestamp with time zone 같은 풍부한 네이티브 타입을 제공하고, Hibernate도 PostgreSQL Dialect에서 UUID 같은 타입을 자연스럽게 매핑합니다.

프로젝트 규칙:

- 의미가 분명한 PostgreSQL 네이티브 타입을 우선 사용한다
- varchar 하나로 모든 것을 표현하는 구조를 지양한다
- 애플리케이션 문자열 파싱 로직으로 DB 타입 의미를 대신하지 않는다

### 3.3 JPA/Hibernate 타입과 DB 타입은 충돌하지 않게 맞춘다

Hibernate는 Java 기본 타입과 java.time 타입을 SQL 타입으로 매핑합니다. 따라서 자바 쪽 의미와 PostgreSQL 쪽 타입 의미를 어긋나게 두면 조회/직렬화/시간대 처리에서 혼란이 생길 수 있습니다.

프로젝트 규칙:

- Java 타입 의미와 DB 타입 의미를 같이 본다
- OffsetDateTime 같은 시점 타입을 쓰면서 DB에 로컬 시각 의미로 저장하는 구조를 지양한다
- UUID를 문자열로 다루지 않고 UUID 타입으로 저장할 수 있으면 그렇게 한다

## 4. 정수 타입 기준

### 4.1 범위에 맞는 정수 타입을 고른다

PostgreSQL은 smallint, integer, bigint 를 제공하며 각각 표현 가능한 범위가 다릅니다.

프로젝트 규칙:

- 기본 정수 타입은 integer 또는 bigint
- 작은 코드성 값이나 작은 범위만 보장되는 값에만 smallint
- 장기 누적되거나 식별자 성격이 강한 값은 bigint 우선
- “혹시 모르니 전부 smallint” 또는 “전부 bigint”를 기계적으로 택하지 않는다

### 4.2 business identifier와 sequence 성격 값은 bigint를 기본 검토한다

이 항목은 Practice + Project Recommendation 이다.

프로젝트 규칙:

- PK 후보, 누적 카운터, 이벤트 번호, 정렬용 일련번호는 bigint를 기본 검토한다
- 현재는 작아 보여도 장기 증가 가능성이 있으면 integer보다 bigint를 선호한다

## 5. 정확 수치 타입 기준

### 5.1 금액/정산/정확도 중요 값은 numeric(p,s)를 사용한다

PostgreSQL 공식 문서에서 numeric 은 정확한 수치 타입이고, real/double precision 은 부정확한 부동소수 타입입니다. 따라서 정확도가 필요한 값에는 numeric 이 맞습니다.

프로젝트 규칙:

다음에는 numeric(p,s) 사용

- 금액
- 수수료
- 환율
- 정산 수치
- 회계 수치
- 정확한 비율 계산값

double precision 을 금액 기본값으로 두지 않는다

### 5.2 precision/scale을 명시한다

프로젝트 규칙:

- numeric 은 가능하면 precision/scale을 명시한다
- 예:
- 금액: numeric(19,4) 같은 형태 검토
- 퍼센트/비율: 도메인에 맞는 scale 명시
- “정확하다”는 이유로 무제한 numeric 을 습관적으로 쓰지 않는다

## 6. 부동소수 타입 기준

### 6.1 근사치가 허용되는 경우에만 real / double precision

PostgreSQL은 real 과 double precision 이 IEEE 754 기반의 inexact type이라고 설명합니다.

프로젝트 규칙:

다음처럼 근사치가 허용되는 경우에만 사용

- 측정값
- 통계값
- 랭킹 점수
- 추천 score
- 과학/센서 데이터

돈, 정산, 계약 수치는 사용 금지

## 7. 문자열 타입 기준

### 7.1 기본 문자열 타입은 text

PostgreSQL 공식 문서는 text, varchar(n), char(n) 사이에 일반적인 성능 차이는 없고, char(n) 은 공백 패딩으로 추가 비용이 있을 수 있다고 설명합니다.

프로젝트 규칙:

- 기본 문자열 타입은 text
- 길이 제한이 도메인 규칙 일 때만 varchar(n)
- char(n) 은 기본 금지

### 7.2 varchar(n) 은 도메인 길이 제약을 표현할 때만 쓴다

프로젝트 규칙:

다음처럼 실제 규칙이 있을 때만 varchar(n) 사용

- 이메일 최대 길이
- 국가 코드 길이
- ISO 코드
- 외부 계약상 길이가 고정된 값

“문자열이면 일단 varchar(255)” 를 기본 금지한다

### 7.3 고정폭 문자열은 기본적으로 피한다

char(n) 은 공백 패딩 특성이 있고, PostgreSQL도 char(n) 사용 시 추가 저장 공간과 일부 처리 비용이 있을 수 있다고 설명합니다.

프로젝트 규칙:

- 고정폭 포맷이 정말 필요한 경우가 아니면 char(n) 사용 금지
- 국가코드, 상태코드 같은 값도 보통 text 또는 varchar(n) 로 충분하다

## 8. UUID 타입 기준

### 8.1 UUID는 문자열이 아니라 uuid 타입으로 저장한다

PostgreSQL은 uuid 타입을 네이티브로 지원하고, UUIDv4/UUIDv7 생성도 지원합니다. Hibernate PostgreSQL Dialect도 UUID를 PostgreSQL UUID 타입으로 매핑합니다.

프로젝트 규칙:

- UUID 의미의 값은 text/varchar(36) 대신 uuid
- 외부 식별자, 공개 식별자, 비순차 식별자에 UUID를 쓴다면 DB 타입도 UUID로 맞춘다
- UUID를 문자열 컬럼에 저장하는 것을 기본 금지한다

### 8.2 UUID 버전 선택은 별도 식별자 정책에서 다룬다

프로젝트 규칙:

- UUID를 쓸지, bigint를 쓸지, UUIDv4/v7 중 무엇을 쓸지는 PK/식별자 기준 문서에서 다룬다
- 이 문서에서는 “UUID를 저장할 때는 uuid 타입을 쓴다”를 기본으로 한다

## 9. 불리언 타입 기준

### 9.1 참/거짓은 boolean

PostgreSQL은 boolean 타입을 제공합니다.

프로젝트 규칙:

다음 같은 참/거짓 상태는 boolean

- 활성/비활성
- 삭제 여부
- 사용 여부
- 잠금 여부

Y/N, 0/1, "true"/"false" 문자열 저장을 기본 금지한다

### 9.2 tri-state가 필요하면 boolean 하나로 우겨 넣지 않는다

프로젝트 규칙:

- true/false/unknown 이 필요하면 nullable boolean, 별도 상태 컬럼, enum/코드 컬럼 중 의미에 맞는 구조를 택한다
- “모름” 상태를 boolean과 주석으로 해결하지 않는다

## 10. 날짜/시간 타입 기준

### 10.1 시점(timestamp)은 기본적으로 timestamp with time zone

PostgreSQL 공식 문서는 시간대가 관련되면 date/time을 따로 쓰기보다 날짜와 시간을 함께 가진 타입을 권장하고, time with time zone 은 권장하지 않는다고 설명합니다.

프로젝트 규칙:

- created_at, updated_at, deleted_at, expires_at, issued_at 같은 시점 은 기본적으로 timestamp with time zone
- Java 쪽은 Instant 또는 OffsetDateTime 우선 검토
- timestamp without time zone 을 시점 저장 기본값으로 두지 않는다

### 10.2 날짜만 필요하면 date

프로젝트 규칙:

다음처럼 시각이 없는 값은 date

- 생년월일
- 영업일
- 정산 기준일
- 이벤트 날짜

날짜만 필요한데 timestamp를 습관적으로 쓰지 않는다

### 10.3 로컬 시각만 의미가 있으면 time 또는 timestamp without time zone 을 예외적으로 쓴다

프로젝트 규칙:

다음처럼 “절대 시점”이 아닌 로컬 시간은 예외적으로 time 또는 로컬 datetime 타입 검토

- 영업 시작 시각
- 반복 스케줄의 로컬 시각
- 매장 오픈 시각

단, time with time zone 은 기본 금지

절대 시점과 로컬 시각 의미를 혼동하지 않는다

## 11. JSON 타입 기준

### 11.1 기본 JSON 저장 타입은 jsonb

PostgreSQL 공식 문서는 json 은 입력 텍스트를 그대로 보존하고, jsonb 는 공백·키 순서·중복 키를 보존하지 않는다고 설명합니다. 반대로 jsonb 는 비교 연산과 인덱싱 등에서 더 실용적입니다.

프로젝트 규칙:

- 반정형 데이터 저장이 정말 필요하면 기본값은 jsonb
- 조회/검색/인덱스 가능성이 있으면 jsonb 우선
- 원문 텍스트 보존이 정말 중요할 때만 json

### 11.2 JSON은 예외적 타입이지 기본 설계 도구가 아니다

프로젝트 규칙:

- 정형 모델로 표현 가능한 값을 무조건 jsonb 로 몰지 않는다
- 핵심 비즈니스 속성, 조인 키, 자주 필터링하는 값은 일반 컬럼 우선
- jsonb 는 확장 필드, 외부 payload 저장, 유연한 metadata 같은 경우에 한정한다

## 12. 바이너리 타입 기준

### 12.1 바이너리 데이터는 bytea

PostgreSQL은 binary data 저장용으로 bytea 를 제공합니다.

프로젝트 규칙:

- 해시값, 서명값, 바이너리 토큰, 작은 바이너리 payload는 bytea
- 바이너리 데이터를 base64 문자열로 억지 저장하지 않는다
- 큰 파일 자체를 DB에 넣을지 여부는 별도 저장 전략 문서에서 다룬다

## 13. ID 생성 관련 타입 기준

### 13.1 숫자 자동 생성 컬럼은 IDENTITY 를 우선 검토한다

PostgreSQL은 identity column을 공식 지원하고, GENERATED ALWAYS AS IDENTITY / GENERATED BY DEFAULT AS IDENTITY 구문을 제공합니다.

프로젝트 규칙:

- 자동 생성 숫자 컬럼은 serial 관성보다 identity 우선 검토
- 기본 타입은 bigint identity를 선호
- PK/FK 전략 자체는 다음 PK 문서에서 더 구체화한다

## 14. enum/상태값 관련 타입 기준

### 14.1 비즈니스 상태값은 DB enum보다 문자열 + 제약을 우선 검토한다

PostgreSQL은 enum 타입도 지원하지만, 이 프로젝트에서는 상태값이 자주 바뀌거나 애플리케이션 enum과 함께 움직일 가능성이 높다면 문자열 컬럼 + check 제약 을 우선 검토한다. 이 부분은 공식 기능이라기보다 Practice + Project Recommendation 이다. PostgreSQL enum 자체는 가능하지만, 상태값 변화 운영성도 같이 봐야 한다.

프로젝트 규칙:

- 상태값 타입 선택은 컬럼 타입만이 아니라 제약/마이그레이션 비용까지 함께 본다
- 자세한 내용은 check 기준, migration 기준 문서와 연결해서 다룬다

## 15. 금지 규칙

다음은 기본 금지다.

- 문자열이면 일괄 varchar(255)
- UUID를 varchar(36) 에 저장
- 돈/정산 값을 double precision 으로 저장
- 시점을 timestamp without time zone 에 습관적으로 저장
- time with time zone 사용
- 핵심 정형 속성을 무분별하게 jsonb 로 저장
- 참/거짓 값을 Y/N, 0/1 문자열로 저장
- 고정폭이 아닌 문자열에 char(n) 사용

## 16. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 컬럼 타입이 값의 의미를 정확히 표현하는가?
- 정밀도가 필요한 값에 numeric 을 사용했는가?
- 시점 컬럼에 timestamp with time zone 을 검토했는가?
- UUID라면 문자열이 아니라 uuid 타입인가?
- 문자열 기본값을 text 로 보고, 실제 길이 제약이 있을 때만 varchar(n) 을 썼는가?
- JSON은 정말 반정형 데이터일 때만 쓰는가?
- JPA/Hibernate 매핑과 PostgreSQL 타입 의미가 충돌하지 않는가?
