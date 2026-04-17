# DB Concurrency 기준

## 1. 목적

이 문서는 Spring + JPA/Hibernate + PostgreSQL 환경에서 동시성 문제를 어떤 방식으로 모델링하고 해결할지 정의한다.

이 문서의 목표는 다음과 같다.

- 동시성 문제를 “락을 걸까 말까” 수준이 아니라 문제 유형별로 나눈다
- 동일 row 동시 수정, 중복 생성, 상태 전이 경쟁, 중복 요청을 서로 다른 방식으로 다룬다
- 기본 해법을 `@Version`, DB 제약, 원자적 SQL, 재시도 정책으로 정리한다
- lock / isolation 문서로 넘겨야 할 문제와 여기서 해결할 문제를 구분한다

Jakarta Persistence는 버전 필드가 있는 엔티티에 대해 provider가 optimistic locking을 자동으로 수행해야 한다고 규정하고, version이 없는 동시 접근 엔티티는 애플리케이션이 직접 데이터 일관성을 책임져야 한다고 설명합니다. PostgreSQL은 `READ COMMITTED`에서 각 명령이 시작 시점의 snapshot으로 대상을 찾고, concurrent update가 있으면 대기 후 `WHERE` 조건을 다시 평가한다고 설명합니다.

## 2. 근거 수준

- Official: PostgreSQL / Jakarta Persistence / Hibernate 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL의 transaction isolation, explicit locking, constraints, `INSERT ... ON CONFLICT`, serialization failure handling 문서와 Jakarta Persistence 3.2의 locking/concurrency 규정, Hibernate User Guide의 optimistic locking 문서를 기준으로 작성한다.

## 3. 기본 원칙

### 3.1 동시성 문제는 한 가지 도구로 풀지 않는다

동일 엔티티의 동시 수정은 optimistic locking으로 다루는 것이 자연스럽고, 중복 생성은 `UNIQUE` 제약이나 `ON CONFLICT`가 더 직접적이며, 큐 선점이나 작업 할당은 row lock 계열이 더 적합하다. PostgreSQL은 제약이 위반되면 저장 자체를 막고, `ON CONFLICT`는 high concurrency에서도 atomic한 INSERT/UPDATE 결과를 보장한다고 설명합니다. Jakarta Persistence는 낙관적 락을 버전 필드 기반으로 정의합니다.

### 3.2 애플리케이션 선조회만으로 정합성을 보장하지 않는다

“먼저 조회해서 없으면 insert”, “먼저 읽고 상태가 READY면 update” 같은 read-then-act 패턴은 경쟁 상태에서 깨질 수 있다. PostgreSQL은 `READ COMMITTED`에서 concurrent updater가 먼저 커밋하면 두 번째 updater가 갱신된 row에 다시 연산을 적용하면서 `WHERE`를 재평가한다고 설명한다. 따라서 선조회 결과를 믿기보다, 제약 또는 단일 SQL 조건식으로 최종 판정을 DB에 맡기는 편이 더 안전하다.

### 3.3 기본 전략은 “낙관적 기본값 + 필요 시 더 강한 수단”이다

Jakarta Persistence는 concurrent access 또는 detached merge가 가능한 엔티티에 optimistic locking 사용을 강하게 권장하고, version이 없으면 inconsistent state, lost update 같은 문제가 생길 수 있다고 설명합니다. 이 기준에 따라 프로젝트 기본값은 동시에 수정될 수 있는 엔티티는 `@Version`으로 보호하고, 더 강한 락이 필요한 경우만 별도 lock 문서 기준으로 확장하는 것이다.

## 4. 동시성 문제 분류 기준

### 4.1 같은 row를 여러 사용자가 수정하는 문제는 optimistic locking으로 본다

JPA는 optimistic locking을 버전 번호 또는 타임스탬프를 읽고, 갱신 시 검증 및 증가시키는 방식으로 정의합니다. Hibernate도 `@Version` 컬럼을 사용해 conflicting updates를 감지하고, last-commit-wins로 덮어써지는 lost update를 막는다고 설명합니다. 따라서 동일 aggregate root를 여러 사용자가 편집하는 문제는 기본적으로 `@Version` 문제다.

### 4.2 중복 생성과 비즈니스 uniqueness는 제약 문제로 본다

PostgreSQL 제약은 저장 시점에 위반을 막고, `UNIQUE`/`PRIMARY KEY`는 동시 insert 경쟁에서도 최종 정합성을 보장한다. 또한 PostgreSQL은 어떤 unique-key failure는 실제로는 직전 읽기와 연관된 serialization류 경쟁의 결과일 수 있다고 설명합니다. 따라서 “중복 생성 방지”는 application lock보다 DB uniqueness가 기본 해법이다.

### 4.3 상태 전이 경쟁은 조건부 UPDATE 문제로 본다

주문 승인, 결제 확정, 세션 폐기처럼 “현재 상태가 특정 값일 때만 전이”되는 작업은 보통 SELECT 후 if 검사보다 `UPDATE ... WHERE status = 'READY'` 같은 단일 SQL이 더 안전하다. PostgreSQL은 concurrent update 뒤 `WHERE` 조건을 다시 평가하므로, 이런 조건부 update는 경쟁 상태에서도 더 직접적인 해법이 된다.

### 4.4 작업 선점과 큐 소비는 lock 문서로 넘긴다

PostgreSQL은 `SELECT ... FOR UPDATE`가 해당 row를 현재 트랜잭션 종료까지 잠그고 다른 UPDATE/DELETE/row lock 요청을 막는다고 설명합니다. 하지만 이런 문제는 낙관적 락 기본 문맥보다 명시적 row lock 문제에 가깝다. 따라서 큐 선점, 작업 할당, 재처리 방지용 row claim은 이 문서의 기본 해법이 아니라 lock.md에서 상세히 다룬다.

## 5. Optimistic Locking 기준

### 5.1 동시에 수정될 수 있는 엔티티는 기본적으로 @Version을 둔다

Jakarta Persistence는 version이 있는 엔티티에 대해 provider가 optimistic locking을 자동 수행해야 하며, concurrent access 또는 detached merge가 가능한 엔티티에는 optimistic locking을 강하게 권장한다고 설명합니다. version이 없으면 lost update와 inconsistent state를 애플리케이션이 직접 막아야 한다고도 명시합니다. 프로젝트 기본값은 동시 수정 가능성이 있는 aggregate root에는 `@Version` 필수다.

### 5.2 기본 버전 타입은 숫자형을 우선한다

Jakarta Persistence는 optimistic locking을 version number 또는 timestamp 전략으로 정의하고, Hibernate는 timestamp도 가능하지만 version number보다 덜 신뢰할 수 있다고 설명합니다. 따라서 프로젝트 기본값은 `long`/`Long` 같은 숫자형 version column이고, timestamp version은 특별한 이유가 있을 때만 검토한다.

### 5.3 version 컬럼은 애플리케이션이 직접 조작하지 않는다

Hibernate는 `@Version` 속성은 entity manager가 conflicting updates를 감지하는 데 사용하며, 애플리케이션이 version number를 임의로 바꾸는 것은 금지라고 설명합니다. 프로젝트 규칙도 동일하다. version은 비즈니스 값이 아니라 concurrency control 메타데이터다.

### 5.4 optimistic lock 실패는 유스케이스 실패로 취급한다

Jakarta Persistence는 optimistic lock failure가 감지되면 provider가 `OptimisticLockException`을 던지고 현재 트랜잭션을 rollback 대상으로 표시해야 한다고 규정합니다. 따라서 이 예외는 단순 warning이 아니라 현재 유스케이스를 다시 판단해야 하는 실패다. 프로젝트에서는 이 예외를 삼키고 계속 진행하지 않는다.

### 5.5 optimistic lock 예외는 flush/commit 시점에 늦게 나타날 수 있다

Jakarta Persistence는 provider가 DB 쓰기를 트랜잭션 끝까지 미룰 수 있고, 이 경우 optimistic lock check도 commit 직전까지 지연될 수 있다고 설명합니다. 예외를 애플리케이션이 더 이른 시점에 처리해야 한다면 `flush()`로 강제 동기화를 유도할 수 있습니다. 프로젝트에서는 “예외가 `save()` 시점에 바로 나올 것”이라고 가정하지 않는다.

### 5.6 versionless optimistic locking은 예외적이고 비표준적이다

Jakarta Persistence는 버전 필드 없이 동작하는 alternative optimistic locking이 provider별로 존재할 수 있지만 portable하지 않다고 설명합니다. Hibernate도 `OptimisticLockType.ALL` / `DIRTY` 같은 versionless optimistic locking을 제공하지만, 이는 provider-specific 기능입니다. 프로젝트 기본값은 **표준 `@Version`**이고, legacy schema 때문에 불가피할 때만 예외적으로 검토한다.

### 5.7 @OptimisticLock(excluded = true)는 아주 제한적으로만 허용한다

Hibernate는 `excluded` 속성은 해당 필드 변경 시 version 증가를 막아, 다른 트랜잭션의 업데이트와 충돌하지 않게 만들 수 있다고 설명합니다. 하지만 그 결과 lost update를 수용해야 하는 필드가 생길 수 있습니다. 프로젝트에서는 조회 카운터처럼 정말 덮어써져도 되는 부수적 필드에만 극히 제한적으로 허용하고, 핵심 비즈니스 상태에는 금지한다.

## 6. DB 제약과 UPSERT 기준

### 6.1 uniqueness와 중복 방지는 제약으로 닫는다

PostgreSQL은 제약 위반 시 저장을 거부하고, 이는 기본값·동시 요청 여부와 무관하게 적용된다고 설명합니다. 따라서 이메일, 외부 id, 비즈니스 natural key, idempotency key 같은 값의 중복 방지는 “먼저 조회해서 없으면 저장”이 아니라 `UNIQUE` 제약으로 닫는다.

### 6.2 insert-or-update는 PostgreSQL-native ON CONFLICT를 우선 검토한다

PostgreSQL은 `ON CONFLICT DO UPDATE`가 high concurrency에서도 atomic한 insert-or-update 결과를 보장한다고 설명하고, `READ COMMITTED`에서도 각 row에 대해 insert 또는 update 둘 중 하나의 결과가 보장된다고 설명합니다. 따라서 “없으면 insert, 있으면 update”는 두 SQL로 나누기보다 UPSERT 한 문장을 우선 검토한다.

### 6.3 ON CONFLICT는 arbiter 제약이 분명할 때만 사용한다

PostgreSQL은 `ON CONFLICT DO UPDATE`가 arbiter unique index 또는 unique/PK 제약을 기준으로 동작하며, NOT DEFERRABLE unique 제약/인덱스만 arbiter로 사용될 수 있다고 설명합니다. `EXCLUDE` 제약은 `ON CONFLICT`의 arbiter로 사용할 수 없습니다. 따라서 UPSERT는 “어떤 uniqueness가 충돌 기준인지”가 스키마에 명확해야 한다. 프로젝트에서는 충돌 기준이 흐린 상태의 포괄적 UPSERT를 금지한다.

### 6.4 duplicate request와 duplicate row는 같은 문제가 아니다

중복 요청은 API idempotency 문제이고, duplicate row 생성은 DB uniqueness 문제다. 둘은 서로 보완적이지만 같은 수단으로 해결하지 않는다. PostgreSQL 제약과 `ON CONFLICT`는 최종 저장 정합성을 보장하지만, 같은 요청 재실행에 대한 응답 재생성 의미까지 대신해 주지는 않는다. 프로젝트에서는 API idempotency는 별도 문서 기준을 따르되, 저장 계층은 여전히 제약으로 닫는다.

## 7. 원자적 상태 전이 기준

### 7.1 read-then-act보다 single-statement mutation을 우선한다

PostgreSQL은 `READ COMMITTED`에서 concurrent updater가 먼저 커밋하면 두 번째 updater가 갱신된 row에 작업을 다시 적용할 수 있고, 이때 `WHERE`가 재평가된다고 설명합니다. 따라서 상태 전이는 SELECT status 후 자바 if문으로 분기하기보다, `UPDATE ... WHERE id = ? AND status = 'READY'` 같은 조건부 mutation을 우선한다.

### 7.2 성공 여부는 조회 결과가 아니라 affected row count/RETURNING으로 판단한다

PostgreSQL `UPDATE`와 `INSERT ... RETURNING`은 실제로 갱신되거나 삽입된 row를 반환할 수 있습니다. 또한 `ON CONFLICT DO UPDATE ... WHERE`에서 조건을 만족하지 않아 update되지 않은 row는 반환되지 않는다고 설명합니다. 따라서 상태 전이와 compare-and-set 계열 로직은 “업데이트 SQL이 1건 반영되었는가”를 기준으로 성공을 판정한다.

### 7.3 숫자 증감과 잔액 차감도 원자적 SQL을 우선 검토한다

`counter = counter + 1`, `stock = stock - 1` 같은 연산은 현재 값을 읽어서 자바에서 계산한 뒤 다시 저장하면 경쟁 상태를 만들기 쉽다. PostgreSQL `UPDATE` 표현식은 기존 컬럼 값을 읽어 새 값을 계산할 수 있으므로, 이런 연산은 DB 표현식 기반 update가 기본이다. 재고 부족 같은 조건이 있으면 `WHERE stock >= :qty`까지 함께 넣어 원자적으로 처리한다. PostgreSQL `UPDATE`는 expression이 기존 컬럼 값을 사용할 수 있다고 설명합니다.

## 8. 재시도 기준

### 8.1 serialization failure와 deadlock은 전체 트랜잭션 재시도 대상이다

PostgreSQL은 `40001`(serialization_failure)은 재시도를 준비해야 하고, `40P01`(deadlock_detected)도 재시도가 적절할 수 있다고 설명합니다. 또한 재시도는 전체 트랜잭션과 그 안의 의사결정 로직 전체를 다시 실행해야 한다고 명시합니다. 프로젝트에서도 부분 SQL만 재실행하지 않고 유스케이스 전체를 다시 수행한다.

### 8.2 unique violation도 때로는 concurrency 재시도 대상일 수 있지만, 무조건은 아니다

PostgreSQL은 `23505`(unique_violation)나 `23P01`(exclusion_violation)도 어떤 경우에는 사실상 serialization류 경쟁의 결과일 수 있다고 설명합니다. 하지만 항상 transient failure는 아니므로, 무조건 자동 재시도하면 persistent business error를 반복할 수 있습니다. 프로젝트에서는 unique violation 재시도는 키 선택 로직이 직전 읽기에 의존한 경우처럼 concurrency 원인이 분명할 때만 제한적으로 허용한다.

### 8.3 optimistic lock 재시도는 application boundary에서만 검토한다

Jakarta Persistence는 optimistic lock failure 시 현재 트랜잭션을 rollback 대상으로 표시한다고 규정합니다. 따라서 한 persistence context 내부에서 예외를 무시하고 계속 진행하는 방식은 안전하지 않다. 프로젝트에서 재시도가 필요하다면, 새 트랜잭션에서 유스케이스 전체를 다시 실행하는 형태로만 검토한다.

### 8.4 자동 재시도는 멱등성과 사용자 의미가 맞을 때만 허용한다

재시도는 기술적으로 가능해도 business semantics가 다르면 위험하다. PostgreSQL도 자동 재시도 시설을 제공하지 않으며, 전체 로직을 다시 실행해야 correctness를 보장할 수 있다고 설명합니다. 프로젝트에서는 “같은 입력으로 다시 실행해도 의미가 같은가”가 분명할 때만 제한적으로 자동 재시도를 허용한다.

## 9. 프로젝트 권장안

### 9.1 aggregate root 동시 수정은 @Version이 기본값

동일 사용자, 주문, 결제, 권한 묶음처럼 한 aggregate root를 여러 요청이 동시에 수정할 수 있다면, 기본 해법은 `@Version`이다. Jakarta Persistence와 Hibernate 모두 이것을 표준적 optimistic locking 메커니즘으로 설명하고, version이 없으면 lost update 가능성이 커진다고 안내합니다.

### 9.2 create-or-ignore / create-or-update는 제약 + UPSERT 우선

중복 생성 경쟁은 row lock보다 uniqueness가 더 직접적이다. 프로젝트에서는 등록, webhook dedup, 외부 이벤트 반영, 자연키 insert는 `UNIQUE` + `ON CONFLICT`를 우선 검토한다. PostgreSQL은 `ON CONFLICT DO UPDATE`가 atomic outcome을 보장한다고 설명합니다.

### 9.3 상태 전이는 “조건부 UPDATE 1건 성공”으로 설계한다

승인/취소/확정/만료 같은 상태 전이는 SELECT 후 분기보다 `UPDATE ... WHERE current_state = ?`를 기본으로 하고, row count 0이면 “이미 다른 트랜잭션이 선점하거나 상태를 바꿨다”로 해석한다. PostgreSQL의 command-level 재평가 semantics와 잘 맞는 방식이다.

### 9.4 명시적 row lock은 정말 필요한 경우만 lock 문서 기준으로 올린다

낙관적 락, 제약, 원자적 update로 해결할 수 있는 문제를 곧바로 `SELECT FOR UPDATE`로 풀지 않는다. row lock은 강력하지만 contention과 대기 비용을 늘릴 수 있고, 이 문서의 기본 전략보다 한 단계 무거운 수단이다. PostgreSQL은 `FOR UPDATE`가 해당 row에 대한 다른 수정/락 획득을 막는다고 설명합니다.

## 10. 문서 경계

이 문서는 동시성 문제 분류와 기본 해법 선택 기준을 다룬다.

다음 내용은 별도 문서에서 확장한다.

- `SELECT FOR UPDATE`, `NOWAIT`, `SKIP LOCKED` 같은 명시적 락
- isolation level 선택과 anomaly 상세
- deadlock 분석
- outbox / 중복 요청 / idempotency 상세
- distributed lock과 cross-process coordination

## 11. 금지 규칙

다음은 기본 금지다.

- 동시에 수정될 수 있는 엔티티에 version 없이 last-commit-wins를 허용하는 것
- `@Version` 대신 애플리케이션 시각이나 임의 숫자를 직접 관리하는 것
- 중복 생성 방지를 “먼저 조회한 뒤 없으면 insert”로만 처리하는 것
- 상태 전이를 SELECT 후 자바 if문 + 별도 UPDATE로 처리하는 것
- `OptimisticLockException`을 잡아서 무시하고 계속 진행하는 것
- `@OptimisticLock(excluded = true)`를 핵심 비즈니스 필드에 사용하는 것
- unique violation을 아무 조건 없이 무한 재시도하는 것
- lock 문제와 optimistic locking 문제를 구분하지 않고 모두 `FOR UPDATE`로 해결하려는 것

이 금지 규칙은 Jakarta Persistence의 optimistic locking 규정, Hibernate의 lost update 방지 설명, PostgreSQL의 제약/UPSERT/serialization failure guidance를 바탕으로 한 best practice다.

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 문제는 같은 row 동시 수정인가, 중복 생성인가, 상태 전이 경쟁인가?
- 동시 수정 가능 엔티티에 `@Version`이 있는가?
- version 타입은 숫자형을 기본으로 검토했는가?
- 중복 생성은 DB `UNIQUE`/`PK`/`FK`로 닫혀 있는가?
- create-or-update는 `ON CONFLICT` 또는 동등한 atomic 방식으로 처리하는가?
- 상태 전이는 단일 `UPDATE ... WHERE ...`로 설계했는가?
- 성공 여부를 row count 또는 `RETURNING`으로 판정하는가?
- `OptimisticLockException`/`40001`/`40P01` 재시도 범위를 전체 유스케이스로 정의했는가?
- unique violation 재시도를 정말 transient concurrency로 해석할 근거가 있는가?
- 락이 꼭 필요한 문제를 optimistic locking 문제와 혼동하고 있지 않은가?
