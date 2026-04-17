# Schema Structure 기준

## 1. 목적

이 문서는 PostgreSQL schema를 어떻게 나누고, 어떤 경우에 추가 schema를 만들며, 애플리케이션이 schema를 어떤 방식으로 참조할지 정의한다.

이 문서의 목표는 다음과 같다.

- schema를 namespace와 운영 경계로 일관되게 사용한다
- public 과 search_path 에 대한 암묵 의존을 줄인다
- JPA/Hibernate 매핑과 migration 구조가 충돌하지 않게 한다
- 멀티 schema 도입을 “필요해서” 하는지, “습관처럼” 하는지 구분한다

## 2. 근거 수준

- Official: PostgreSQL / Hibernate 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 제약 위에 일반적인 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조에 맞춘 규칙

## 3. 기본 원칙

### 3.1 schema는 namespace다

PostgreSQL에서 schema는 객체를 담는 namespace이고, 같은 데이터베이스 안에서 이름 충돌을 분리하는 단위다. 객체를 스키마 없이 참조하면 search_path 를 따라 찾고, 현재 스키마는 search_path 의 첫 번째 스키마다. 스키마를 명시하지 않고 CREATE TABLE 같은 DDL을 실행하면 현재 스키마에 생성된다.

프로젝트 규칙:

- schema는 단순 폴더 비슷한 개념이 아니라 객체 해석과 생성 위치를 결정하는 DB 경계로 본다
- schema 구조는 migration, 권한, 기본 스키마 설정과 함께 결정한다

### 3.2 이 프로젝트의 기본값은 “애플리케이션 전용 단일 schema”다

PostgreSQL은 기본적으로 public 스키마를 두고, unqualified name은 search_path 를 따라 해석한다. 하지만 공식 문서가 search_path 에 포함된 스키마를 신뢰 문제와 연결해서 설명하는 만큼, 일반 업무 서비스에서는 애플리케이션 테이블을 public 에 흩뿌리기보다 전용 schema 하나에 모으는 쪽이 더 안전하고 명확하다.

프로젝트 규칙:

- 기본 구조는 서비스당 하나의 애플리케이션 전용 schema
- 예: auth, project_auth, pas_auth
- 애플리케이션 테이블을 기본 public 스키마에 두는 것을 기본값으로 두지 않는다

### 3.3 public 과 search_path 에 암묵적으로 기대지 않는다

PostgreSQL 공식 문서는 search_path 가 이름 해석과 생성 위치를 바꾸고, 그 path 안의 스키마는 신뢰 경계가 된다고 설명한다. 또한 public 스키마는 기본으로 존재하지만 특별한 의미가 있는 것은 아니고, 필요하면 CREATE 권한을 회수할 수도 있다고 설명한다.

프로젝트 규칙:

- migration에서는 생성 대상 schema를 명시한다
- 운영 DB에서 애플리케이션 객체를 public 에 생성하는 것을 기본 금지한다
- search_path 가 우연히 맞아서 동작하는 구조를 지양한다
- “로컬에서는 되는데 운영에서는 안 되는” schema 해석 차이를 만들지 않는다

### 3.4 JPA/Hibernate의 기본 스키마는 한 곳에서 정한다

Hibernate는 엔티티에 schema를 지정하지 않으면 현재 DB 연결의 기본 스키마를 사용하고, 필요하면 @Table(schema = "...") 로 스키마를 지정할 수 있다고 설명한다.

프로젝트 규칙:

- 기본 schema는 DB 연결/ORM 설정에서 한 번 정하는 것을 우선한다
- 같은 애플리케이션에서 모든 엔티티에 @Table(schema = "...") 를 반복해서 박는 구조를 기본값으로 두지 않는다
- 엔티티별 schema 지정은 멀티 schema가 정말 필요한 경우에만 허용한다

### 3.5 멀티 schema는 명확한 운영 이유가 있을 때만 도입한다

PostgreSQL은 여러 schema를 지원하고, Hibernate도 schema별 매핑을 지원한다. 하지만 공식 문서 어디에도 “레이어마다 schema를 나눠라” 같은 권장은 없고, schema는 결국 namespace/권한/해석 경계다.

프로젝트 규칙:

다음처럼 이유가 분명할 때만 멀티 schema를 검토한다.

- 테넌트별 schema 분리
- 운영상 강한 권한 분리
- 외부 시스템이 만든 객체와 애플리케이션 객체 분리
- 감사/audit 전용 schema 분리
- 레거시 공존

반대로 다음 이유만으로는 기본 도입하지 않는다.

- 패키지 레이어별 분리
- “깔끔해 보인다”는 이유
- 도메인마다 무조건 schema를 쪼개려는 습관

## 4. 권장 구조

### 4.1 기본 권장 구조

프로젝트 기본 권장 구조:

- 애플리케이션 테이블: 전용 schema 하나
- PostgreSQL 내장/system 객체: 기본 system schema
- 필요 시 audit/history 전용 schema 별도 검토
- 필요 시 extension이 요구하는 별도 schema 검토

프로젝트 규칙:

- 기본 업무 테이블은 한 schema에 모은다
- schema를 늘릴 때는 “이 schema가 어떤 운영 책임을 분리하는가”를 설명할 수 있어야 한다

### 4.2 audit/history schema는 예외적으로 분리할 수 있다

Hibernate Envers는 audit table의 기본 schema를 따로 둘 수 있고, 별도 audit schema 구성이 가능하다고 설명한다.

프로젝트 규칙:

- audit/history 테이블이 많고 운영 목적이 분명하면 별도 schema 검토 가능
- 다만 업무 테이블과 audit 테이블을 무조건 다른 schema로 분리하는 것을 기본값으로 두지 않는다
- audit schema 분리는 조회 패턴, 권한, migration 운영성을 함께 보고 결정한다

### 4.3 멀티테넌시용 schema 분리는 별도 전략으로 다룬다

Hibernate 문서는 schema-per-tenant 방식과 단일 schema + discriminator 방식을 구분해 설명한다.

프로젝트 규칙:

- 멀티테넌시를 한다면 schema 구조 문서에서 살짝 언급만 하지 말고 별도 전략 문서로 분리한다
- tenant schema 구조와 일반 서비스 단일 schema 구조를 같은 규칙으로 섞지 않는다

## 5. 권한 규칙

### 5.1 애플리케이션 객체를 두는 schema는 명시적 권한 경계로 본다

PostgreSQL은 schema에 대해 USAGE, CREATE 권한을 구분하고, public 스키마의 CREATE 권한을 회수하는 패턴도 문서에 예시로 보여 준다.

프로젝트 규칙:

- 애플리케이션 schema는 필요한 역할만 USAGE/CREATE 를 갖게 한다
- 운영 애플리케이션 계정에 불필요한 광범위 schema 권한을 주지 않는다
- public schema에 대한 CREATE 권한을 그대로 열어 둘지 기본 검토한다

### 5.2 migration 계정과 runtime 계정의 책임을 구분할 수 있다

이 항목은 주로 Practice + Project Recommendation 이다.

프로젝트 규칙:

- migration 실행 계정은 schema 변경 권한을 가질 수 있다
- runtime 애플리케이션 계정은 DDL 권한 없이 DML 중심 권한만 갖도록 분리할 수 있다
- schema 구조를 정할 때 권한 운영 모델도 함께 설계한다

## 6. 이름 규칙

### 6.1 schema 이름은 짧고 명확하게 둔다

PostgreSQL은 schema 이름이 기존 schema와 달라야 하고, pg_ 로 시작하는 이름은 시스템 schema용으로 예약돼 있다고 설명한다.

프로젝트 규칙:

- schema 이름은 소문자 snake_case를 기본으로 한다
- pg_ 접두사는 사용 금지
- 너무 일반적인 이름(app, data)보다 서비스/기능 의미가 드러나는 이름을 쓴다

권장 예:

- auth
- auth_audit
- billing
- billing_audit

비권장 예:

- pg_auth
- schema1
- appdata

## 7. JPA / Native SQL / Migration 정렬 규칙

### 7.1 ORM과 migration이 같은 schema를 바라봐야 한다

Hibernate는 현재 연결의 기본 schema 또는 @Table(schema=...) 를 기준으로 매핑하고, PostgreSQL은 search_path 또는 명시 schema 기준으로 객체를 찾는다. 두 기준이 어긋나면 ORM은 한 schema를 보고 migration은 다른 schema에 테이블을 만들 수 있다.

프로젝트 규칙:

- migration 대상 schema와 ORM 기본 schema를 일치시킨다
- native SQL이 있다면 그 SQL도 같은 schema 규칙을 따른다
- 로컬/테스트/운영에서 schema가 달라지지 않게 한다

### 7.2 native SQL은 schema 규칙을 더 엄격히 본다

이 항목은 Practice + Project Recommendation 이다.

프로젝트 규칙:

- native SQL은 search_path 의 우연한 해석에 기대지 않게 한다
- 운영에서 search_path가 달라져도 문제 없게 schema 명시 여부를 일관되게 정한다
- JPA entity 매핑과 native SQL 참조 방식이 서로 다른 schema를 가리키지 않게 한다

## 8. migration과의 관계

### 8.1 schema 생성은 migration으로 관리한다

PostgreSQL은 CREATE SCHEMA 를 통해 schema를 만들고, schema 안 객체를 생성할 수 있다고 설명한다.

프로젝트 규칙:

- schema 생성/변경은 migration 도구로 관리한다
- 애플리케이션 부팅 시 우연히 schema가 생기길 기대하지 않는다
- “운영 DB에 수동으로 하나 만들어 둔 상태”를 기본 전제로 두지 않는다

### 8.2 schema 존재 자체도 버전 관리 대상이다

프로젝트 규칙:

- 테이블만이 아니라 schema 생성/권한/기본 객체도 migration 이력에 남긴다
- 새 환경에서 migration만으로 같은 schema 구조를 재현할 수 있어야 한다

## 9. 금지 규칙

다음은 기본 금지다.

- 애플리케이션 테이블을 무비판적으로 public 에 생성
- search_path 우연 해석에 기대는 구조
- 모든 엔티티에 같은 @Table(schema=...) 반복
- 레이어별 분리만을 이유로 schema를 여러 개 도입
- migration 대상 schema와 ORM 기본 schema 불일치
- schema 이름에 pg_ 접두사 사용
- runtime 계정에 불필요한 schema CREATE 권한 부여

## 10. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 서비스는 기본적으로 하나의 전용 schema를 사용하는가?
- public 과 search_path 에 암묵적으로 기대지 않는가?
- migration과 ORM이 같은 schema를 바라보는가?
- 멀티 schema 도입 이유를 운영 관점에서 설명할 수 있는가?
- schema 권한이 명시적으로 통제되는가?
- native SQL도 같은 schema 규칙을 따르는가?
