# PK / FK / UNIQUE / CHECK 기준

## 1. 목적

이 문서의 목표는 다음과 같다.

- 행 식별자와 비즈니스 고유성을 구분한다
- 참조 무결성을 애플리케이션 로직이 아니라 DB 제약으로 보장한다
- 행 단위 불변식과 관계 무결성을 서로 다른 수단으로 표현한다
- JPA/Hibernate 사용 시에도 해석이 흔들리지 않는 안전한 기본값을 만든다

PostgreSQL 공식 문서는 PRIMARY KEY, UNIQUE, FOREIGN KEY, CHECK를 서로 다른 의미의 제약으로 설명하며, 특히 cross-row / cross-table 규칙은 CHECK보다 UNIQUE나 FOREIGN KEY 같은 더 직접적인 수단으로 표현하라고 안내한다.

## 2. 근거 수준

- Official: PostgreSQL / Jakarta Persistence 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

PostgreSQL 공식 문서는 PK/UNIQUE/FK/CHECK의 의미와 제약 범위를 명확히 정의하고 있고, Jakarta Persistence 공식 문서는 복합 PK가 별도의 primary key class를 요구한다고 설명한다. 이 둘을 함께 봐야 JPA 기반 프로젝트에서 신뢰도 높은 기준이 된다.

## 3. 기본 원칙

### 3.1 제약은 “검증 로직”이 아니라 “데이터 의미”를 표현해야 한다

PRIMARY KEY는 행의 대표 식별자, UNIQUE는 대체 식별자 또는 비즈니스 고유성, FOREIGN KEY는 참조 무결성, CHECK는 같은 행 안에서 평가 가능한 불변식을 표현한다. 제약의 의미를 섞으면 스키마 해석이 불안정해진다. PostgreSQL도 각 제약을 이런 역할로 구분한다.

### 3.2 애플리케이션 중복 검사만으로 끝내지 말고 DB 제약으로 닫아야 한다

중복 방지, 부모 존재 보장, 행 내부 불변식은 서비스 코드에서 한 번 확인하는 것으로 충분하지 않다. PostgreSQL 제약은 저장 시점에 위반을 막는 마지막 안전장치다. 기본 정책은 “애플리케이션 검증 + DB 제약” 이중 방어다.

### 3.3 제약 선택은 JPA 매핑 비용과도 충돌하지 않아야 한다

PostgreSQL은 복합 PK를 지원하지만, JPA에서는 복합 PK가 별도의 primary key class와 더 복잡한 매핑 규칙을 요구한다. 따라서 DB에서 가능하다는 이유만으로 복합 PK를 기본값으로 두는 것은 실무적으로 불리하다.

## 4. PRIMARY KEY 기준

### 4.1 모든 애플리케이션 테이블은 명시적 PRIMARY KEY를 둔다

PostgreSQL은 모든 테이블에 PK가 강제되지는 않지만, 공식 문서도 “일반적으로 그렇게 하는 것이 가장 좋다”고 설명한다. PK는 행의 대표 식별자이고, FK의 기본 참조 대상이며, 클라이언트와 ORM이 행을 안정적으로 식별하는 기준이 된다.

프로젝트 규칙:

- 모든 애플리케이션 테이블은 명시적 PK를 가진다
- 로그성/임시성 테이블도 특별한 이유가 없으면 PK를 둔다
- “어차피 unique가 있으니 PK는 없어도 된다”를 허용하지 않는다

### 4.2 기본 PK 전략은 단일 컬럼 surrogate key

PostgreSQL에서 PK는 단일 컬럼도, 복합 컬럼도 가능하다. 다만 JPA는 복합 PK에 대해 @EmbeddedId 또는 @IdClass 기반의 primary key class를 요구한다. 실무에서는 대부분의 엔티티에 단일 컬럼 surrogate key를 두고, 비즈니스 식별자는 UNIQUE로 분리하는 쪽이 매핑과 운영에 더 안전하다.

프로젝트 규칙:

- 기본 PK는 id 단일 컬럼
- 비즈니스 식별자, 공개 식별자, 외부 식별자는 PK가 아니라 UNIQUE
- 복합 PK는 조인 테이블 또는 “조합 자체가 정체성”인 경우에만 예외적으로 허용

### 4.3 PK는 UNIQUE + NOT NULL의 의미를 명확하게 드러내는 제약이다

PostgreSQL 공식 문서에 따르면 PK는 사실상 UNIQUE + NOT NULL과 동등한 저장 제약을 가지며, 생성 시 unique B-tree 인덱스도 자동으로 만든다. 하지만 PK는 단순 고유 제약이 아니라 “대표 식별자”라는 의미를 추가로 부여한다.

프로젝트 규칙:

- “대표 식별자”가 아닌 컬럼에 PK를 두지 않는다
- 고유하더라도 nullable 하거나 변경 가능성이 큰 컬럼은 PK로 두지 않는다
- PK 이름은 명시적으로 선언한다

### 4.4 숫자 자동 생성 PK는 IDENTITY를 기본 검토하되, IDENTITY만으로 유일성이 보장된다고 생각하지 않는다

PostgreSQL의 identity column은 암묵 시퀀스에서 값을 생성하지만, 공식 문서상 identity 자체는 uniqueness를 보장하지 않는다. 유일성은 PRIMARY KEY 또는 UNIQUE가 별도로 보장해야 한다.

프로젝트 규칙:

- 숫자 자동 생성 PK는 GENERATED ... AS IDENTITY를 기본 검토
- identity 컬럼은 반드시 PK 또는 UNIQUE와 함께 사용
- “자동 증가니까 중복이 없을 것”이라는 가정만으로 설계하지 않는다

## 5. UNIQUE 기준

### 5.1 비즈니스 고유성은 UNIQUE로 표현한다

PostgreSQL의 unique constraint는 단일 컬럼뿐 아니라 복합 컬럼 조합에도 적용할 수 있고, 생성 시 unique B-tree 인덱스를 자동으로 만든다. 이메일, 외부 subject, tenant 내부 natural key처럼 “중복되면 안 되는 값”은 서비스 로직이 아니라 UNIQUE로 닫아야 한다.

프로젝트 규칙:

- 비즈니스상 중복 금지 값은 DB UNIQUE로 표현
- 조합 고유성은 복합 UNIQUE 사용
- 단순 조회 인덱스와 고유성 제약을 혼동하지 않는다

### 5.2 nullable UNIQUE의 의미를 명시적으로 설계한다

PostgreSQL에서 UNIQUE는 기본적으로 NULL을 서로 같은 값으로 보지 않는다. 따라서 nullable unique 컬럼에는 여러 개의 NULL이 저장될 수 있다. 하나의 NULL만 허용하려면 NULLS NOT DISTINCT가 필요하다.

프로젝트 규칙:

- nullable unique는 기본적으로 재검토한다
- “값이 없을 수 있음”과 “값이 없을 때도 하나만 허용”을 구분한다
- 필요하면 NOT NULL, NULLS NOT DISTINCT, 또는 별도 모델링으로 의도를 명확히 한다

### 5.3 조건부 고유성은 UNIQUE constraint가 아니라 partial unique index

PostgreSQL 공식 문서는 “일부 행에만 적용되는 uniqueness restriction”은 unique constraint로 쓸 수 없고 unique partial index로 표현해야 한다고 설명한다. soft delete, 활성 데이터만 유일, 특정 상태에서만 유일 같은 요구는 이 범주다.

프로젝트 규칙:

다음 같은 요구는 partial unique index로 표현한다.

- deleted_at is null 조건에서만 유일
- is_active = true 인 행만 유일
- 특정 상태 집합에서만 유일

### 5.4 UNIQUE는 “대체 식별자”이지 PK 대용 기본값이 아니다

UNIQUE가 있다고 해서 PK가 불필요해지는 것은 아니다. PostgreSQL도 PK는 테이블의 대표 식별자이고, FK 기본 참조 대상이며, 한 테이블에 하나만 둘 수 있는 특별한 제약이라고 설명한다. 실무 best practice는 PK와 business unique를 분리하는 것이다.

프로젝트 규칙:

- PK와 business unique는 역할을 분리한다
- 이메일, username, external_id는 대부분 UNIQUE
- 대표 식별자만 PK

## 6. FOREIGN KEY 기준

### 6.1 참조 무결성은 FOREIGN KEY로 표현한다

PostgreSQL의 FK는 자식 테이블 값이 부모 테이블의 어떤 행과 일치해야 함을 보장한다. 참조 대상은 PK, unique constraint, 또는 non-partial unique index여야 한다. 부모 존재 보장을 코드에만 맡기지 않고 FK로 닫는 것이 기본이다.

프로젝트 규칙:

- 연관 관계는 가능하면 FK를 건다
- “애플리케이션이 알아서 맞출 것”이라는 이유로 FK를 생략하지 않는다
- FK 없는 조인은 예외적 상황에서만 허용

### 6.2 필수 관계는 FK + NOT NULL, 선택 관계만 nullable FK

PostgreSQL 공식 문서상 FK는 참조 컬럼이 NULL이면 제약을 회피할 수 있다. 필수 관계를 보장하려면 FK만으로는 부족하고 NOT NULL이 함께 있어야 한다. 또한 PostgreSQL은 CHECK (col IS NOT NULL)보다 explicit NOT NULL이 더 효율적이라고 설명한다.

프로젝트 규칙:

- 필수 부모 관계: FK + NOT NULL
- 선택 관계: nullable FK 허용
- “필수 관계인데 nullable FK”를 기본 금지

### 6.3 ON DELETE 정책은 라이프사이클 관계를 기준으로 고른다

PostgreSQL 공식 문서는 NO ACTION이 기본값이며, RESTRICT는 더 엄격하고, CASCADE는 부모 삭제 시 자식도 함께 삭제한다고 설명한다. 또한 자식이 부모의 구성요소라면 CASCADE가 적절할 수 있지만, 두 테이블이 독립 객체라면 RESTRICT 또는 NO ACTION이 더 적절하다고 안내한다.

프로젝트 규칙:

- 기본값: NO ACTION 또는 RESTRICT
- 부모 없이는 존재 의미가 없는 구성요소 테이블에만 CASCADE
- 선택 관계를 끊는 의미가 분명할 때만 SET NULL
- aggregate 경계를 넘는 무분별한 CASCADE 삭제는 금지

### 6.4 FK는 참조하는 쪽에 인덱스를 자동 생성하지 않는다

PostgreSQL은 참조 대상 쪽에는 PK/UNIQUE로 인해 인덱스가 확보되지만, 참조하는 쪽 컬럼에는 FK 선언만으로 인덱스를 자동 생성하지 않는다. 다만 부모 삭제나 참조 값 변경 시 referencing table scan이 필요할 수 있으므로, 공식 문서도 referencing columns 인덱스를 자주 권장한다.

프로젝트 규칙:

- FK 컬럼은 조인 경로 / 삭제 경로 / 갱신 경로를 보고 인덱스를 검토한다
- “FK가 있으니 인덱스도 자동 생성된다”는 가정을 금지한다
- 인덱스 상세 기준은 별도 index 문서에서 다룬다

### 6.5 복합 FK는 식별 규칙이 조합 단위일 때만 사용한다

PostgreSQL은 복합 FK를 지원하지만, 컬럼 수와 타입이 정확히 맞아야 하고, nullable 처리도 더 신중해야 한다. MATCH FULL까지 고려해야 하는 경우가 생기므로, 실무적으로는 정말 조합 단위 식별이 필요한 경우에만 쓰는 것이 안전하다.

프로젝트 규칙:

- 복합 FK는 기본값이 아니다
- tenant scoped key, 복합 PK 참조처럼 의미가 분명할 때만 허용
- 혼합 null 상태 문제가 우려되면 NOT NULL 또는 MATCH FULL 검토

## 7. CHECK 기준

### 7.1 CHECK는 같은 행 안에서 평가 가능한 불변식에만 사용한다

PostgreSQL의 check constraint는 Boolean 식이 true 또는 NULL이면 통과한다. 공식 문서는 CHECK가 기본적으로 새로 삽입되거나 수정되는 그 행만 대상으로 해야 하며, 다른 행이나 다른 테이블 데이터를 참조하는 규칙 표현에는 적합하지 않다고 설명한다.

프로젝트 규칙:

다음 같은 규칙에만 CHECK를 사용한다.

- 수치 범위
- 시작/종료 순서
- 상호 배타 컬럼 조합
- 상태와 값의 행 단위 일관성

### 7.2 null 금지는 CHECK가 아니라 NOT NULL

PostgreSQL 공식 문서는 CHECK가 식 결과가 NULL이어도 통과한다고 설명하고, CHECK (column IS NOT NULL)보다 명시적 NOT NULL이 더 효율적이라고 안내한다.

프로젝트 규칙:

- null 금지는 항상 NOT NULL
- CHECK (col IS NOT NULL)을 기본 금지
- 필수 컬럼 대부분은 명시적으로 NOT NULL

### 7.3 cross-row / cross-table 규칙은 CHECK로 우회하지 않는다

PostgreSQL은 다른 행 또는 다른 테이블 데이터를 참조하는 CHECK를 지원 대상으로 보지 않으며, dump/restore 시 깨질 수 있다고 경고한다. 그런 경우 UNIQUE, FOREIGN KEY, EXCLUDE, 또는 필요 시 trigger를 사용하라고 공식 문서가 안내한다.

프로젝트 규칙:

- 중복 금지는 UNIQUE
- 부모 존재 보장은 FK
- 기간 겹침 같은 특수 제약은 EXCLUDE 검토
- 단발성 교차 검사만 필요하면 trigger를 예외적으로 검토
- cross-row / cross-table 규칙을 CHECK 서브쿼리로 해결하려 하지 않는다

### 7.4 CHECK는 immutable한 조건이어야 한다

PostgreSQL은 CHECK 조건이 같은 입력에 항상 같은 결과를 내는 immutable 조건이라고 가정한다. 사용자 정의 함수 동작을 나중에 바꾸면 기존 행이 제약을 위반하게 될 수 있고, 이후 dump/restore에서 실패할 수 있다.

프로젝트 규칙:

- CHECK에는 단순 비교식, 범위식, enum 유사 값 검증처럼 안정적인 식만 사용
- 동작 변경 가능성이 있는 사용자 정의 함수 의존을 지양
- 함수 변경이 필요한 경우 제약 drop/re-add까지 고려한다

## 8. 제약 이름 기준

PostgreSQL은 제약에 명시적 이름을 부여할 수 있고, 이는 오류 메시지 해석과 변경 작업을 더 명확하게 만든다. 운영과 migration 추적성을 위해 자동 생성 이름에 의존하지 않는 편이 안전하다.

프로젝트 규칙:

```text
pk_<table>
uq_<table>__<columns>
fk_<from_table>__<to_table>
ck_<table>__<rule>
```

형식으로 명시적으로 선언한다.

## 9. 금지 규칙

다음은 기본 금지다.

- PK 없이 UNIQUE만 두고 대표 식별자를 생략
- 필수 관계인데 nullable FK만 두기
- 일부 행에만 필요한 uniqueness를 일반 UNIQUE로 억지 표현
- nullable UNIQUE를 두고 “NULL도 하나만 들어갈 것”이라고 가정
- 독립 객체 관계에 무분별하게 ON DELETE CASCADE
- null 금지를 CHECK (col IS NOT NULL)로 표현
- cross-row / cross-table 규칙을 CHECK로 해결
- identity 컬럼만 두고 PK/UNIQUE 없이 유일성이 보장된다고 간주

위 금지 규칙은 PostgreSQL 공식 동작과 실무 운영 리스크를 함께 반영한 best practice다.

## 10. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 테이블의 대표 식별자는 명시적 PK인가?
- 비즈니스 고유성은 PK가 아니라 UNIQUE로 분리했는가?
- nullable UNIQUE의 NULL 의미를 명확히 설계했는가?
- 조건부 uniqueness가 필요하면 partial unique index를 검토했는가?
- 필수 부모 관계는 FK + NOT NULL인가?
- ON DELETE 정책이 라이프사이클 관계와 맞는가?
- FK 참조 컬럼 인덱스 필요 여부를 검토했는가?
- CHECK는 같은 행 안에서만 평가되는가?
- null 금지를 CHECK가 아니라 NOT NULL로 표현했는가?
- 제약 이름을 명시적으로 선언했는가?
