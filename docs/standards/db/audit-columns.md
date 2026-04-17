# Audit Columns 기준

## 1. 목적

이 문서는 PostgreSQL 테이블에서 생성 시각, 수정 시각, 생성 주체, 수정 주체 같은 감사 컬럼을 어떤 기준으로 두고 채울지 정의한다.

이 문서의 목표는 다음과 같다.

- 감사 컬럼을 테이블마다 제멋대로 두지 않고 공통 규칙으로 표준화한다
- 시간 컬럼과 주체 컬럼의 의미를 분리한다
- DB 기본값, trigger, Spring Data JPA auditing, Hibernate timestamp 기능 중 무엇을 어디까지 맡길지 정한다
- soft delete, 전체 변경 이력(audit log), version/concurrency 컬럼과 역할을 섞지 않는다

PostgreSQL은 컬럼 기본값으로 `CURRENT_TIMESTAMP` 같은 표현식을 둘 수 있고, 그 식은 row 삽입 시 평가된다고 설명한다. Spring Data JPA는 `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`를 제공하고, Hibernate는 `@CreationTimestamp`, `@UpdateTimestamp`를 제공한다. 즉 시간과 주체를 채우는 기술 수단은 여러 개가 있지만, 어떤 컬럼을 왜 두는지는 프로젝트 표준이 먼저 정해야 한다.

## 2. 근거 수준

- Official: PostgreSQL / Spring Data JPA / Hibernate 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL의 default value, date/time function, trigger, system information 함수 문서와 Spring Data JPA auditing, Hibernate `@CreationTimestamp` / `@UpdateTimestamp` 문서를 기준으로 작성한다. PostgreSQL은 `CURRENT_TIMESTAMP`와 `now()`가 transaction start time 의미를 갖는다고 설명하고, trigger는 `NEW` row를 수정해 반환할 수 있다고 설명한다. Spring Data JPA는 현재 principal을 `AuditorAware<T>`로 제공하도록 정의하고, Hibernate는 timestamp 값을 JVM 메모리 또는 DB에서 생성하도록 선택할 수 있다고 설명한다.

## 3. 기본 원칙

### 3.1 감사 컬럼은 “변경 이력 저장소”가 아니라 “현재 row의 메타데이터”다

`created_at`, `updated_at`, `created_by`, `updated_by`는 현재 row가 언제 누구에 의해 만들어지고 마지막으로 바뀌었는지를 담는 컬럼이다. 전체 변경 히스토리를 남기는 audit table, CDC, outbox, event log와는 역할이 다르다. PostgreSQL 공식 문서도 `CURRENT_TIMESTAMP`를 row insertion 시각 같은 기본값 예시로 설명하고, Spring Data JPA auditing 역시 “누가/언제 만들고 바꿨는지”를 엔티티 메타데이터로 다룬다.

### 3.2 시간 감사 컬럼과 주체 감사 컬럼은 분리해서 설계한다

시간 컬럼은 “언제”를, 주체 컬럼은 “누가”를 표현한다. 시간만 필요한 테이블도 있고, 주체까지 필요한 테이블도 있다. Spring Data JPA도 날짜 계열만 추적하는 경우 `AuditorAware`가 필요 없다고 설명한다. 따라서 프로젝트에서는 `created_at`/`updated_at`을 기본 세트로 보고, `created_by`/`updated_by`는 실제 운영·보안·추적 요구가 있을 때 추가한다.

### 3.3 한 컬럼은 한 가지 권위 있는 채움 방식만 가진다

`updated_at`을 DB trigger도 갱신하고 애플리케이션 auditing도 갱신하면, 어떤 값이 source of truth인지 흐려진다. Hibernate의 `@CreationTimestamp` / `@UpdateTimestamp`는 기본적으로 JVM 메모리에서 값을 만들 수 있고, PostgreSQL의 `CURRENT_TIMESTAMP`는 DB에서 생성된다. 따라서 같은 컬럼을 두 체계가 동시에 관리하게 두지 않는다. 프로젝트 기본 원칙은 한 감사 컬럼당 하나의 authoritative writer다.

## 4. 표준 감사 컬럼 세트

### 4.1 기본 필수 세트는 created_at, updated_at

프로젝트 기본 감사 컬럼 세트는 다음 두 컬럼이다.

- `created_at`
- `updated_at`

이 둘은 대부분의 비즈니스 테이블에서 현재 row의 생성 시점과 마지막 수정 시점을 설명하는 최소 메타데이터다. PostgreSQL은 timestamp 기본값에 `CURRENT_TIMESTAMP`를 쓰는 것을 대표 예시로 제시하고, Spring Data JPA도 `@CreatedDate`, `@LastModifiedDate`를 기본적인 auditing metadata로 제시한다.

### 4.2 선택 세트는 created_by, updated_by

다음 컬럼은 운영/보안/추적 요구가 있을 때 선택적으로 둔다.

- `created_by`
- `updated_by`

Spring Data JPA는 `@CreatedBy`, `@LastModifiedBy`를 통해 “누가 생성·수정했는지”를 저장할 수 있고, 이를 위해 `AuditorAware<T>`가 현재 principal을 제공해야 한다고 설명한다. 프로젝트에서는 이 요구가 있는 도메인에만 주체 컬럼을 추가한다. 모든 테이블에 기계적으로 붙이지는 않는다.

### 4.3 다음 컬럼들은 이 문서의 기본 감사 컬럼 범위에 넣지 않는다

- `deleted_at` / `deleted_by`: soft delete 문서에서 별도로 다룬다
- `version`: 낙관적 락 / concurrency 문서에서 다룬다
- 변경 사유, 변경 요청 id, 전체 이전값/이후값: audit log / domain event / outbox 범위다

이 구분은 PostgreSQL 기능 차이라기보다 프로젝트 문서 경계에 대한 규칙이다. 현재 문서 순서에서도 soft delete는 별도 주제로 분리되어 있다.

## 5. 시간 감사 컬럼 기준

### 5.1 시간 감사 컬럼 타입은 timestamp with time zone

PostgreSQL은 date/time 타입을 제공하고, `CURRENT_TIMESTAMP` 같은 함수는 `timestamp with time zone` 의미로 동작한다. 프로젝트의 이전 column-types 기준과도 일치하게, 감사 시각 컬럼은 로컬 시각이 아니라 절대 시점을 표현해야 하므로 `timestamp with time zone`을 기본으로 한다.

### 5.2 created_at은 NOT NULL + DB 기본값을 기본으로 한다

PostgreSQL은 default value 식이 row 삽입 시 평가되며, `CURRENT_TIMESTAMP`를 timestamp column default의 전형적 예시로 설명한다. 프로젝트 기본값은 `created_at timestamp with time zone not null default current_timestamp`다. 생성 시각은 삽입 시점 메타데이터이므로, 애플리케이션이 매번 수동으로 채우게 두기보다 DB 기본값으로 닫는 편이 더 안정적이다.

### 5.3 updated_at은 insert 시점과 update 시점을 모두 고려해 채운다

`updated_at`은 insert 때도 값이 있어야 하고, 이후 row가 바뀔 때마다 갱신되어야 한다. PostgreSQL의 default value는 insert 시점에만 적용되므로, `updated_at default current_timestamp`만으로는 update 반영까지 해결되지 않는다. 따라서 `updated_at`은 insert 초기값은 default로 두되, update 시점 갱신은 DB trigger 또는 애플리케이션 auditing 중 하나로 별도 책임을 둬야 한다.

### 5.4 CURRENT_TIMESTAMP는 “실제 벽시계 현재 시각”이 아니라 transaction start time이다

PostgreSQL은 `transaction_timestamp()`가 `CURRENT_TIMESTAMP`와 같고, `now()`도 전통적 동등어라고 설명한다. 반면 `statement_timestamp()`는 현재 statement 시작 시각, `clock_timestamp()`는 실제 현재 시각으로 statement 안에서도 변할 수 있다고 설명한다. 프로젝트에서는 일반 감사 컬럼의 기본 의미를 트랜잭션 기준 시각으로 두고, 특별히 벽시계 실시간이 필요하지 않다면 `CURRENT_TIMESTAMP`를 사용한다.

### 5.5 default에서는 TIMESTAMP 'now' 같은 literal 형태를 사용하지 않는다

PostgreSQL은 later evaluation이 필요한 `DEFAULT` 절에서 `TIMESTAMP 'now'` 형태를 쓰지 말라고 명시한다. 이 형태는 상수가 파싱될 때 고정되어, 실제로는 table creation 시점 값이 들어갈 수 있기 때문이다. 프로젝트에서도 default에는 `CURRENT_TIMESTAMP` 또는 `now()` 같은 함수형 표현만 허용한다.

## 6. 주체 감사 컬럼 기준

### 6.1 created_by / updated_by는 기본적으로 “애플리케이션 principal”을 저장한다

Spring Data JPA는 `AuditorAware<T>`가 “현재 애플리케이션과 상호작용하는 current user or system”을 제공하도록 정의한다. 따라서 웹 애플리케이션에서 주체 감사 컬럼은 보통 DB role 이름이 아니라, 서비스가 인지하는 사용자/시스템 actor 식별자를 저장하는 것이 맞다. 프로젝트 기본값도 이 해석을 따른다.

### 6.2 current_user / session_user를 애플리케이션 사용자 식별자로 기본 사용하지 않는다

PostgreSQL은 `session_user`가 DB 연결을 시작한 사용자이고, `current_user`는 권한 검사에 쓰이는 사용자이며 `SET ROLE`이나 `SECURITY DEFINER`로 바뀔 수 있다고 설명한다. 이 값들은 DB 세션/권한 맥락에는 유용하지만, 일반적인 애플리케이션 사용자 principal과는 다를 수 있다. 특히 connection pool을 쓰는 서비스에서는 “DB 계정”과 “최종 사용자”가 거의 항상 다르다. 그래서 프로젝트에서는 `created_by` / `updated_by`를 DB의 `current_user`에 기본적으로 매핑하지 않는다.

### 6.3 주체 컬럼 타입은 도메인 식별자 규칙에 맞춘다

주체 컬럼은 문자열, 숫자, UUID 중 어떤 것이든 가능하지만, 중요한 것은 현재 애플리케이션 actor를 안정적으로 식별할 수 있는 값이어야 한다는 점이다. Spring Data JPA의 `AuditorAware<T>`도 제네릭 타입 `T`로 주체 컬럼 타입을 자유롭게 정할 수 있게 설계되어 있다. 프로젝트에서는 보통 사용자 PK나 시스템 actor code 같은 내부 식별자를 저장하고, 표시용 이름은 감사 컬럼에 넣지 않는다.

## 7. 값을 채우는 방식 기준

### 7.1 created_at 기본값은 DB가 채우는 것을 기본으로 한다

PostgreSQL이 default value를 row insertion 시 평가해 주므로, `created_at`은 DB 기본값으로 채우는 것이 가장 단순하고 신뢰도가 높다. 이 값은 insert 경로가 JPA이든 native SQL이든 배치든 일관되게 적용된다. 프로젝트 기본값은 `created_at`을 애플리케이션 코드에서 직접 세팅하지 않고, DB 기본값으로 닫는 것이다.

### 7.2 updated_at의 프로젝트 기본값은 DB trigger다

PostgreSQL의 BEFORE ROW trigger는 `NEW` row를 수정해서 반환할 수 있고, UPDATE 전용 trigger를 만들 수 있으며, `WHEN (OLD.* IS DISTINCT FROM NEW.*)` 같은 조건도 줄 수 있다고 설명한다. 프로젝트에서는 `updated_at`을 DB trigger로 유지하는 것을 기본 권장안으로 둔다. 이유는 이 방식이 JPA 엔티티 저장, native SQL, 운영 스크립트, 배치 업데이트처럼 여러 write path를 가장 일관되게 커버하기 때문이다. 이것은 공식 기능 위에 얹는 프로젝트 best practice다.

### 7.3 단, ORM auditing을 쓴다면 DB trigger와 섞지 않는다

Spring Data JPA auditing은 `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`를 제공하고, Hibernate `@CreationTimestamp` / `@UpdateTimestamp`도 timestamp 생성을 지원한다. 다만 Hibernate는 두 annotation의 기본 source가 VM(in memory)라고 설명하고, Spring Data auditing의 시간 공급자도 기본적으로 애플리케이션 쪽 `CurrentDateTimeProvider`다. 따라서 ORM auditing을 채택하면, 같은 컬럼을 DB trigger가 다시 덮어쓰지 않도록 한쪽만 authoritative writer로 선택해야 한다.

### 7.4 클러스터/다중 writer 환경의 시간 일관성이 중요하면 DB time source를 우선한다

Hibernate는 timestamp source를 VM 또는 DB로 선택할 수 있다고 설명한다. 프로젝트에서는 여러 애플리케이션 인스턴스, 여러 write path, 배치/운영 SQL까지 함께 고려해야 하는 감사 컬럼이라면, 시간 source는 DB가 더 보수적이고 일관된 기본값이다. 따라서 시간 감사 컬럼은 DB source, 주체 감사 컬럼은 애플리케이션 principal source로 나누는 구성이 기본 권장안이다. 이는 공식 기능 조합 위에 얹는 프로젝트 best practice다.

## 8. 변경 의미 기준

### 8.1 created_at / created_by는 삽입 후 불변이다

생성 감사 컬럼은 row가 처음 만들어진 사실을 설명하므로, 이후 update에서 바뀌면 안 된다. Hibernate도 `@CreationTimestamp`는 insert 시 한 번만 생성된다고 설명한다. 프로젝트에서는 생성 감사 컬럼을 비즈니스 코드에서 수정하지 않으며, 필요하면 DB 권한/trigger/ORM 매핑으로 보호한다.

### 8.2 updated_at / updated_by는 마지막 유효 변경을 반영한다

수정 감사 컬럼은 row가 마지막으로 바뀐 시점을 나타내므로, update 시 갱신되어야 한다. Hibernate `@UpdateTimestamp`는 row update마다 재생성된다고 설명하고, PostgreSQL trigger는 변경된 `NEW` row를 반환해 저장 row를 바꿀 수 있다. 프로젝트에서는 “마지막 유효 변경”의 기준을 명확히 두고, `updated_at`을 사람이 임의로 세팅하는 방식은 금지한다.

### 8.3 no-op update에까지 updated_at을 바꿀지 여부는 명시적으로 정한다

PostgreSQL `CREATE TRIGGER` 문서는 `WHEN (OLD.* IS DISTINCT FROM NEW.*)` 또는 특정 컬럼이 실제 바뀐 경우에만 trigger를 실행하는 예시를 보여 준다. 프로젝트 기본값은 실제 row 값이 바뀐 경우에만 `updated_at`을 바꾸는 것이다. 단순 재저장이나 동일값 overwrite까지 모두 “수정”으로 간주할지 여부는 팀 규칙으로 명시해야 한다.

## 9. 프로젝트 권장안

### 9.1 기본 권장 조합

프로젝트 기본 권장 조합은 다음과 같다.

- `created_at timestamptz not null default current_timestamp`
- `updated_at timestamptz not null default current_timestamp`
- `updated_at`은 BEFORE UPDATE row trigger로 갱신
- `created_by` / `updated_by`는 필요할 때만 추가
- 주체 컬럼은 `AuditorAware` 등으로 애플리케이션 principal을 채움
- 시간 컬럼과 주체 컬럼은 서로 다른 source를 가져도 되지만, 같은 컬럼에 이중 writer를 두지 않음

이 조합은 PostgreSQL의 default/trigger 기능과 Spring Data JPA의 auditing principal 모델을 가장 안정적으로 결합하는 프로젝트 권장안이다.

### 9.2 DB role 이름을 감사 주체로 저장하는 것은 예외적이다

DB 내부 배치, 직접 SQL 운영 도구, 보안성 높은 DB 중심 워크플로처럼 DB 세션 주체 자체가 의미 있는 시스템이 아니라면, `current_user` / `session_user`를 row 감사 주체의 기본값으로 삼지 않는다. PostgreSQL은 이 값들이 DB 연결/권한 문맥을 나타낸다고 설명하고, Spring Data JPA는 애플리케이션 principal을 `AuditorAware`로 공급하도록 설계한다. 프로젝트 기본값은 애플리케이션 actor 식별자다.

## 10. 문서 경계

이 문서는 현재 row 수준의 감사 컬럼만 다룬다.

다음 내용은 별도 문서에서 확장한다.

- `deleted_at` / `deleted_by`와 soft delete
- 전체 변경 이력 테이블
- CDC / outbox / event log
- 낙관적 락용 version
- 운영 감사 로그와 DB session audit

현재 문서 체계에서도 soft delete는 별도 주제로 남아 있다.

## 11. 금지 규칙

다음은 기본 금지다.

- `created_at` / `updated_at` 없이 테이블마다 제각각 감사 컬럼을 두는 것
- 시간 감사 컬럼을 로컬 시각 문자열로 저장하는 것
- `DEFAULT TIMESTAMP 'now'` 같은 literal 형태를 사용하는 것
- 같은 `updated_at` 컬럼을 DB trigger와 ORM auditing이 동시에 관리하는 것
- `created_by` / `updated_by`를 애플리케이션 principal 대신 DB `current_user`에 기본 매핑하는 것
- 생성 감사 컬럼을 business code가 임의로 수정하는 것
- `updated_at` 의미를 정하지 않은 채 no-op update와 실제 변경을 섞어 쓰는 것
- `deleted_at`이나 `version`을 기본 감사 컬럼과 같은 범주로 취급하는 것

이 금지 규칙은 PostgreSQL default/trigger/`current_user` semantics와 Spring Data JPA / Hibernate auditing semantics를 운영 규칙으로 압축한 것이다.

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 테이블의 최소 감사 컬럼이 `created_at`, `updated_at`으로 표준화되어 있는가?
- 시간 감사 컬럼 타입이 절대 시점을 표현하는 타입인가?
- `created_at`은 DB default로 안전하게 채워지는가?
- `updated_at`의 authoritative writer가 하나로 정해져 있는가?
- `created_by` / `updated_by`가 필요하다면 actor source가 애플리케이션 principal로 정의되어 있는가?
- DB role 이름과 애플리케이션 사용자 식별자를 혼동하지 않는가?
- 생성 감사 컬럼은 삽입 후 불변으로 취급되는가?
- no-op update에 대한 `updated_at` 정책이 팀 규칙으로 정해져 있는가?
- soft delete / version / full audit log와 문서 경계가 섞이지 않는가?
