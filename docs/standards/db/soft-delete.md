# Soft Delete 기준

## 1. 목적

이 문서는 PostgreSQL에서 row를 물리 삭제하지 않고 논리적으로 삭제 상태로 전환하는 soft delete를 어떤 기준으로 설계할지 정의한다. 이 문서의 목표는 다음과 같다. 첫째, soft delete를 단순 boolean 플래그가 아니라 상태 전이와 조회 계약으로 다룬다. 둘째, active row 조회 기준, uniqueness, FK, 복구, purge를 분리해서 설계한다. 셋째, Hibernate/Spring Data JPA를 사용할 때 ORM 차원의 soft delete와 DB 차원의 정합성 규칙을 혼동하지 않게 만든다. Hibernate는 soft delete를 “row를 실제 삭제하지 않고, 더 이상 active하지 않음을 나타내는 컬럼을 갱신하는 것”으로 설명한다.

## 2. 근거 수준

- Official: PostgreSQL / Hibernate / Spring Data JPA 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL의 partial index, unique constraint/index, foreign key 규칙과 Hibernate의 `@SoftDelete`, Spring Data JPA의 delete/bulk delete 동작 문서를 기준으로 작성한다. PostgreSQL은 일부 행에만 적용되는 uniqueness는 unique constraint가 아니라 unique partial index로 강제해야 한다고 설명하고, FK 대상은 non-partial unique index 또는 PK/UNIQUE 제약이어야 한다고 설명한다. Hibernate는 `@SoftDelete`가 truth-based와 TIMESTAMP 전략을 지원한다고 설명한다. Spring Data JPA는 bulk delete 계열이 persistence context를 동기화하지 않고, 일부 배치 delete는 JPA cascade/lifecycle event도 존중하지 않는다고 설명한다.

## 3. 기본 원칙

### 3.1 soft delete는 “DELETE의 다른 문법”이 아니라 “상태 전이”다

soft delete는 row를 제거하는 것이 아니라, row를 비활성/삭제 상태로 바꾸는 것이다. Hibernate도 soft delete를 실제 삭제 대신 “더 이상 active하지 않음을 표시하는 컬럼 갱신”으로 설명한다. 따라서 soft delete가 도입된 테이블에서는 삭제가 곧 `DELETE`가 아니라, 보통 `UPDATE ... SET deleted_at = ...` 또는 그에 상응하는 상태 전이로 해석되어야 한다.

### 3.2 soft delete는 조회 계약이 함께 정의되어야 한다

row를 남겨 두기만 하고 조회 기본값을 정하지 않으면 soft delete는 의미가 반쯤만 구현된 것이다. PostgreSQL의 partial index 문서는 partial predicate를 만족하는 일부 row만 인덱싱할 수 있다고 설명하고, planner가 query의 `WHERE` 조건이 그 predicate를 함의한다고 인식해야 해당 인덱스를 사용할 수 있다고 설명한다. 프로젝트에서는 active row 기본 조회 조건을 `deleted_at IS NULL` 같은 단순하고 일관된 predicate로 고정하는 것을 기본값으로 둔다.

### 3.3 soft delete는 DB 무결성 규칙을 없애지 않는다

soft delete된 row도 DB에는 계속 존재한다. 따라서 FK는 여전히 그 row를 “존재하는 row”로 본다. PostgreSQL은 FK가 참조 컬럼 값이 대상 테이블의 어떤 row와 일치해야 한다고 설명한다. 또 일반 unique constraint는 테이블 전체에 적용되고, 일부 row에만 적용되는 uniqueness는 partial unique index로 따로 표현해야 한다고 설명한다. 즉 soft delete는 FK/UNIQUE를 자동으로 soft-delete-aware하게 바꾸지 않는다.

## 4. soft delete 표현 방식 기준

### 4.1 프로젝트 기본 soft delete 컬럼은 deleted_at

Hibernate는 `@SoftDelete`가 truth-based 전략과 TIMESTAMP 전략을 모두 지원하고, TIMESTAMP 전략은 row가 삭제된 시점을 추적한다고 설명한다. 프로젝트 기본 권장안은 boolean 플래그보다 `deleted_at timestamp with time zone null` 을 soft delete indicator로 두는 것이다. 이 방식은 “삭제되었는가?”뿐 아니라 “언제 삭제되었는가?”까지 한 컬럼으로 표현할 수 있고, active row 조건도 `deleted_at IS NULL`로 자연스럽다. 이것은 Hibernate의 TIMESTAMP 전략과도 잘 맞는 프로젝트 권장안이다.

### 4.2 deleted_at 타입은 timestamp with time zone

삭제 시점은 절대 시점이어야 하므로, 프로젝트의 기존 시간 타입 기준과 일관되게 `timestamp with time zone`을 사용한다. Hibernate soft delete의 TIMESTAMP 전략도 삭제 시점을 추적하는 전략으로 설명된다. 프로젝트에서는 soft delete를 단순 상태 플래그가 아니라 운영 시점 정보로 보기 때문에, 로컬 시각이나 문자열보다 절대 시점 타입을 기본으로 한다.

### 4.3 deleted_by는 선택 컬럼으로 둔다

누가 삭제했는지까지 운영상 중요한 도메인이라면 `deleted_by`를 추가할 수 있다. 이는 이전 audit-columns 문서의 `created_by` / `updated_by`와 같은 성격의 actor metadata다. 다만 soft delete의 최소 핵심은 `deleted_at`이며, `deleted_by`는 보안·감사 요구가 있을 때 추가하는 선택 항목으로 둔다. 이 구분은 Hibernate가 soft delete 자체를 indicator column 중심으로 설명하는 점 위에 얹는 프로젝트 권장안이다.

### 4.4 boolean-only soft delete는 기본값으로 두지 않는다

Hibernate는 truth-based soft delete도 지원하지만, TIMESTAMP 전략도 공식적으로 지원하며, TIMESTAMP 전략은 삭제 시점을 추적한다고 설명한다. 프로젝트에서는 운영 추적성 때문에 boolean-only (`deleted`, `is_deleted`)보다 `deleted_at`를 기본값으로 둔다. boolean 전략은 도입 가능하지만, 프로젝트 표준 기본값은 아니다.

## 5. 조회 기준

### 5.1 일반 조회의 기본 predicate는 deleted_at IS NULL

PostgreSQL partial index 문서는 partial index가 일부 row만 인덱싱하고, query planner가 query `WHERE` 조건이 index predicate를 함의한다고 인식해야 사용된다고 설명한다. 프로젝트에서는 soft delete 테이블의 일반 조회 기본 predicate를 `deleted_at IS NULL` 로 통일한다. 이렇게 해야 active row만 대상으로 하는 partial index와 partial unique index를 단순하게 맞출 수 있다.

### 5.2 관리자/복구/감사 조회만 삭제 row를 명시적으로 포함한다

soft delete의 기본 의미는 “일반 비즈니스 경로에서는 보이지 않아야 한다”는 것이다. 따라서 삭제 row를 포함하는 조회는 예외 경로로 분리하고, 일반 repository/query method가 이를 묵시적으로 포함하지 않게 한다. 이 원칙은 partial index predicate를 query와 일관되게 맞추라는 PostgreSQL planner 규칙 위에 얹는 운영 best practice다.

### 5.3 active row predicate는 단순하고 동일한 형태를 유지한다

PostgreSQL은 partial index가 사용되려면 query의 `WHERE` 조건이 index predicate를 수학적으로 함의한다고 planner가 알아야 하고, 일반적인 theorem prover는 없으며, matching은 planning time에 일어난다고 설명한다. 또한 parameterized clause는 partial index와 잘 맞지 않는다고 설명한다. 따라서 프로젝트에서는 active row filter를 `deleted_at IS NULL`처럼 항상 같은 단순 표현으로 유지한다.

## 6. uniqueness 기준

### 6.1 active row만 unique해야 하면 partial unique index를 사용한다

PostgreSQL은 전체 테이블이 아니라 일부 row에만 적용되는 uniqueness restriction은 unique constraint로 쓸 수 없고, unique partial index로 표현해야 한다고 설명한다. soft delete에서 가장 대표적인 요구는 “삭제되지 않은 row끼리만 이메일/코드/외부 id가 유일해야 한다”는 것이다. 프로젝트에서는 이런 요구를 `UNIQUE` 제약이 아니라 `WHERE deleted_at IS NULL` partial unique index로 표현한다.

### 6.2 soft delete 후 같은 natural key를 재사용할 수 있게 할지 명시적으로 정한다

partial unique index를 쓰면 active row 사이의 uniqueness만 강제되고, soft-deleted row는 uniqueness 대상에서 빠질 수 있다. 따라서 삭제 후 같은 이메일/코드를 다시 등록할 수 있게 된다. 이것이 맞는지 아닌지는 비즈니스 정책이다. PostgreSQL은 일부 row에만 uniqueness를 강제할 수 있다고 설명하므로, 프로젝트에서는 재사용 허용 여부를 명시적으로 결정하고, partial unique index를 그 정책에 맞게 사용한다.

### 6.3 restore는 uniqueness를 다시 만족해야 한다

soft-deleted row를 복구하면 그 row는 다시 active subset에 들어간다. active subset에 partial unique index가 걸려 있다면, 동일 natural key를 가진 다른 active row가 이미 존재할 때 restore는 실패해야 한다. 이는 PostgreSQL unique partial index semantics의 직접적인 결과다. 프로젝트에서는 restore를 단순 플래그 복원으로 보지 않고, active uniqueness를 다시 통과해야 하는 상태 전이로 본다.

## 7. FK와 연관관계 기준

### 7.1 soft delete는 FK를 자동으로 끊지 않는다

PostgreSQL은 FK가 참조 컬럼 값이 대상 row와 일치해야 한다고 설명한다. soft delete는 row를 지우지 않으므로, 참조 대상 row는 여전히 존재한다. 따라서 부모를 soft delete해도 자식 FK는 기본적으로 그대로 유효하다. 프로젝트에서는 soft delete가 “관계 제거”가 아니라 “row 비활성화”임을 전제로 설계한다.

### 7.2 자식 존재를 이유로 부모 soft delete를 막을지 여부를 별도 규칙으로 둔다

DB FK는 soft-deleted parent를 특별 취급하지 않는다. 따라서 “활성 자식이 있는 부모는 soft delete 금지” 같은 규칙이 필요하면, 그것은 FK 자체가 아니라 애플리케이션 규칙 또는 추가 제약 설계의 문제다. 프로젝트에서는 이런 규칙을 도메인 서비스/유스케이스 레벨에서 명시적으로 다룬다. 이는 PostgreSQL FK가 존재성만 보장한다는 공식 의미 위에 얹는 best practice다.

### 7.3 partial unique index 위의 natural key는 FK 참조 대상으로 기본 사용하지 않는다

PostgreSQL은 FK가 참조할 대상 컬럼이 non-deferrable unique/primary key 제약 또는 non-partial unique index여야 한다고 설명한다. 따라서 soft delete 때문에 active row만 unique하도록 partial unique index를 만든 natural key는 FK target으로 적합하지 않다. 프로젝트에서는 soft-delete-aware natural key가 있더라도 FK는 안정적인 surrogate PK를 참조하는 것을 기본값으로 둔다.

## 8. 삭제 동작 기준

### 8.1 soft delete는 기본적으로 UPDATE다

Hibernate soft delete도 실제 삭제 대신 indicator column을 갱신하는 방식이라고 설명한다. 따라서 프로젝트에서 soft delete는 `DELETE` SQL이 아니라, 보통 `UPDATE ... SET deleted_at = current_timestamp` 또는 ORM의 soft delete 기능이 만들어내는 update semantics로 해석한다.

### 8.2 일반 delete 경로와 physical purge 경로를 분리한다

soft delete가 있는 테이블에서도 영구 삭제가 아예 불가능한 것은 아니다. 다만 일반 비즈니스 삭제와 운영 purge는 다른 행위다. 프로젝트에서는

- 일반 비즈니스 삭제: soft delete
- 운영 정리/보존기간 만료 purge: physical delete

를 분리한다. 이 구분은 Hibernate가 soft delete를 “실제 삭제 대신 indicator 갱신”으로 설명하는 점 위에 얹는 프로젝트 규칙이다.

### 8.3 soft-deletable 엔티티에서 bulk delete를 기본 금지한다

Spring Data JPA는 `deleteAllInBatch`, `deleteAllByIdInBatch` 같은 배치 delete가 단일 query를 생성하고, persistence context를 DB와 동기화하지 않으며, JPA cascade semantics와 lifecycle events도 존중하지 않는다고 설명한다. 또한 JPQL bulk delete와 Criteria bulk delete도 DB 직접 delete로 매핑되고 persistence context를 동기화하지 않는다고 설명한다. soft delete가 ORM lifecycle, entity mapping, soft-delete annotation/정책에 의존한다면 이런 bulk delete는 그 경로를 우회할 수 있으므로, 프로젝트에서는 soft-deletable 엔티티에 대한 bulk physical delete를 기본 금지하고, purge 전용 경로에서만 명시적으로 사용한다.

## 9. Hibernate / JPA 기준

### 9.1 Hibernate를 쓴다면 @SoftDelete를 공식 선택지로 본다

Hibernate는 `@SoftDelete`를 1급 기능으로 제공하고, entity와 collection table(`@ElementCollection`, `@ManyToMany`)에 적용할 수 있다고 설명한다. 또한 TIMESTAMP 전략과 truth-based 전략을 지원하고, TIMESTAMP 전략은 삭제 시각을 추적한다고 설명한다. 프로젝트에서는 Hibernate 6.4+/7.x 기능셋을 사용하는 경우, entity soft delete에 `@SoftDelete(strategy = TIMESTAMP, columnName = "deleted_at")`를 공식 후보로 본다.

### 9.2 @SoftDelete는 @OneToMany 컬렉션에 붙이지 않는다

Hibernate는 `@SoftDelete`를 collection table 기반인 `@ElementCollection`과 `@ManyToMany`에는 적용할 수 있지만, `@OneToMany` association에 붙이면 예외를 던진다고 설명한다. 따라서 프로젝트에서는 엔티티 자체를 soft delete하거나, join/collection table에만 제한적으로 적용한다. `@OneToMany`의 자식 엔티티 soft delete는 자식 엔티티 자체가 soft-deletable해야 한다.

### 9.3 provider-specific soft delete와 DB 표준은 구분한다

JPA 표준 자체는 soft delete를 표준 annotation으로 정의하지 않는다. Hibernate `@SoftDelete`는 유용한 공식 기능이지만 provider-specific이다. 프로젝트에서는 ORM 기능을 쓰더라도 DB 차원의 predicate, partial unique index, FK 설계는 별도로 명시한다. 즉 ORM 기능은 편의 수단이지 정합성 그 자체가 아니다. 이 원칙은 Hibernate soft delete 기능과 PostgreSQL partial index/FK 규칙을 함께 볼 때 자연스럽다.

## 10. 프로젝트 권장안

### 10.1 기본 soft delete 스키마

프로젝트 기본 권장안은 다음과 같다.

- `deleted_at timestamp with time zone null`
- 필요 시 `deleted_by`
- 일반 조회 기본 predicate는 `deleted_at IS NULL`
- active-row uniqueness는 partial unique index
- FK는 surrogate PK 기준 유지
- physical purge는 별도 배치/운영 경로 분리

이 구성은 Hibernate의 TIMESTAMP soft delete 전략, PostgreSQL의 partial unique index, FK target 제약 규칙을 함께 고려한 프로젝트 기본값이다.

### 10.2 soft delete는 “조회 은닉 + 정합성 유지 + 나중 purge”까지 포함해서 설계한다

단순히 `deleted_at`만 추가해 두고 조회, uniqueness, restore, purge를 정하지 않으면 soft delete는 반쪽 설계다. PostgreSQL 공식 문서가 제공하는 것은 partial index, unique partial index, FK 규칙 같은 building block이고, 프로젝트는 이 위에 active-row 계약을 얹는다. 따라서 soft delete 도입 시 반드시 조회 기본값, active uniqueness, restore 실패 가능성, purge 경로를 함께 정의한다.

## 11. 문서 경계

이 문서는 논리 삭제 상태와 active-row 계약을 다룬다.

다음 내용은 별도 문서에서 확장한다.

- `created_at` / `updated_at` / `deleted_by` 같은 감사 컬럼 세부
- purge job과 보존 기간 정책
- outbox/event sourcing/전체 변경 이력
- multi-tenant row visibility
- 낙관적 락과 복구 시 version 충돌

현재 문서 체계에서도 audit-columns, concurrency, migration은 이미 별도 문서로 분리되어 있다.

## 12. 금지 규칙

다음은 기본 금지다.

- soft delete indicator만 두고 일반 조회 predicate를 표준화하지 않는 것
- soft-deleted row가 있는데도 전체-table `UNIQUE`가 active-only uniqueness를 대신해 줄 것이라 기대하는 것
- soft-delete-aware natural key를 partial unique index로 두고, 그 키를 FK target으로 사용하려는 것
- restore가 active uniqueness를 다시 만족해야 한다는 점을 무시하는 것
- soft-deletable 엔티티에 대해 `deleteAllInBatch`, JPQL bulk delete, Criteria bulk delete를 일반 삭제 경로로 사용하는 것
- `@SoftDelete`를 `@OneToMany` 컬렉션에 붙이는 것
- 일반 목록/API 조회에 삭제 row를 묵시적으로 섞는 것
- soft delete와 physical purge를 같은 경로로 다루는 것

이 금지 규칙은 PostgreSQL partial unique index/FK 규칙, Hibernate `@SoftDelete` 제약, Spring Data JPA bulk delete semantics를 운영 규칙으로 압축한 것이다.

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- soft delete indicator가 `deleted_at` 기준으로 일관되게 정의되어 있는가?
- 일반 조회 기본 predicate가 `deleted_at IS NULL`로 고정되어 있는가?
- active-row uniqueness가 필요하면 partial unique index로 설계했는가?
- restore 시 uniqueness 재검증이 필요하다는 점을 고려했는가?
- FK가 soft-deleted parent를 자동으로 차단하지 않는다는 점을 알고 있는가?
- natural key partial unique index를 FK target으로 삼지 않았는가?
- soft delete 경로와 physical purge 경로를 분리했는가?
- soft-deletable 엔티티에서 bulk delete가 ORM soft delete semantics를 우회하지 않는가?
- Hibernate `@SoftDelete`를 쓴다면 provider-specific 기능임을 알고 있는가?
- `@OneToMany` 컬렉션에 `@SoftDelete`를 잘못 적용하지 않았는가?
