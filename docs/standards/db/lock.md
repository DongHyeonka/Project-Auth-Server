# Lock 기준

## 1. 목적

이 문서는 PostgreSQL과 JPA/Hibernate 환경에서 명시적 락(explicit lock) 을 어떤 기준으로 사용할지 정의한다.

이 문서의 목표는 다음과 같다.

- 낙관적 락, 제약, 원자적 UPDATE로 해결 가능한 문제와 명시적 락이 필요한 문제를 구분한다
- PostgreSQL의 row-level lock과 table-level lock을 혼동하지 않게 한다
- `FOR UPDATE`, `FOR NO KEY UPDATE`, `FOR SHARE`, `FOR KEY SHARE`, `NOWAIT`, `SKIP LOCKED`의 의미를 안전하게 해석한다
- Spring Data JPA의 `@Lock`과 JPA `PESSIMISTIC_*`를 PostgreSQL 락 의미와 연결해서 사용한다

PostgreSQL은 explicit locking이 MVCC만으로 원하는 동작을 얻을 수 없을 때 애플리케이션이 직접 사용하는 도구라고 설명한다. 따라서 락은 기본값이 아니라 예외적 수단으로 보는 것이 맞다.

## 2. 근거 수준

- Official: PostgreSQL / Jakarta Persistence / Spring Data JPA 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL의 explicit locking, SELECT locking clause, timeout 설정 문서와 Jakarta Persistence 3.2의 `LockModeType`, pessimistic lock timeout/scope, Spring Data JPA의 `@Lock` 문서를 근거로 한다.

## 3. 기본 원칙

### 3.1 명시적 락은 기본 해법이 아니다

PostgreSQL은 explicit locking을 “MVCC가 원하는 동작을 주지 않을 때” 사용하는 application-controlled locking이라고 설명한다. 따라서 프로젝트 기본 원칙은 다음 순서다. 먼저 `UNIQUE`/`FK`/`CHECK` 같은 제약, `ON CONFLICT`, 조건부 UPDATE, `@Version` 같은 더 직접적이고 덜 무거운 수단을 검토하고, 그래도 해결되지 않을 때만 명시적 락을 올린다.

### 3.2 기본 대상은 table lock이 아니라 row lock이다

PostgreSQL은 table-level lock과 row-level lock을 구분하며, 일반적인 비즈니스 동시성 제어에서는 row-level lock이 더 직접적이다. 또한 일반 SELECT는 `ACCESS SHARE`를 잡고, plain SELECT를 막는 유일한 table-level lock은 `ACCESS EXCLUSIVE` 라고 설명한다. 프로젝트 기본값은 `LOCK TABLE`이 아니라 필요한 row만 잠그는 것이다.

### 3.3 락은 가능한 짧게 잡는다

PostgreSQL은 row-level lock과 table-level lock이 보통 트랜잭션 종료 시점까지 유지되고, savepoint 이후 잡은 락은 savepoint rollback 시 해제된다고 설명한다. 또한 deadlock을 피하는 최선의 방어는 일관된 순서로 잠그는 것과 트랜잭션을 길게 잡지 않는 것이라고 설명한다. 프로젝트에서도 락을 잡은 뒤 외부 호출, 사용자 대기, 긴 계산을 넣지 않는다.

## 4. PostgreSQL 락 모델 기준

### 4.1 row-level lock은 일반 조회를 막지 않는다

PostgreSQL은 row-level lock이 plain read를 막지 않고, 같은 row에 대한 writer와 locker만 막는다고 설명한다. 따라서 row lock은 “모든 접근 차단”이 아니라 “같은 row의 경쟁 갱신/락 획득 제어”로 이해해야 한다. 이 점을 오해하면 불필요하게 table lock을 선택하게 된다.

### 4.2 join query의 락 범위는 명시적으로 제한한다

PostgreSQL `SELECT ... FOR ...` 문법은 `FOR lock_strength [ OF from_reference ... ]` 형태를 지원하고, table list를 생략하면 statement에 사용된 모든 테이블에 적용될 수 있다고 설명한다. 따라서 join query에서 특정 alias만 잠그려면 `OF o`처럼 대상을 명시하는 것이 더 안전하다. 프로젝트에서는 join query + row lock 조합에서 락 대상 alias 명시를 기본 검토한다.

### 4.3 LIMIT과 락을 함께 쓰면 충분한 row를 찾는 시점까지만 잠근다

PostgreSQL은 locking clause와 `LIMIT`를 함께 쓰면 필요한 수의 row를 반환할 만큼까지만 locking이 진행된다고 설명한다. 하지만 `OFFSET`으로 건너뛴 row도 잠길 수 있다고 명시한다. 따라서 queue claim 같은 락 기반 조회에서 `OFFSET`은 기본 금지다.

## 5. row-level lock 강도 선택 기준

### 5.1 FOR UPDATE

PostgreSQL은 `FOR UPDATE`가 선택된 row를 현재 트랜잭션 종료 시까지 잠그고, 다른 트랜잭션의 `UPDATE`, `DELETE`, `SELECT FOR UPDATE`, `SELECT FOR NO KEY UPDATE`, `SELECT FOR SHARE`, `SELECT FOR KEY SHARE`를 막는다고 설명한다. 가장 강한 row lock이므로, 삭제 또는 key 변경을 포함한 강한 배타 제어가 필요할 때 사용한다.

### 5.2 FOR NO KEY UPDATE

PostgreSQL은 `FOR NO KEY UPDATE`가 `FOR UPDATE`와 유사하지만 더 약하며, `SELECT FOR KEY SHARE`를 막지 않는다고 설명한다. 또한 key 변경을 동반하지 않는 일반 `UPDATE`는 이 수준의 잠금을 획득한다고 설명한다. 프로젝트에서는 row를 나중에 갱신할 예정이지만 key 보존이 전제인 경우, SQL 레벨에서는 `FOR UPDATE`보다 `FOR NO KEY UPDATE`를 먼저 검토한다.

### 5.3 FOR SHARE

PostgreSQL은 `FOR SHARE`가 shared row lock을 획득하고, 다른 트랜잭션의 `UPDATE`, `DELETE`, `SELECT FOR UPDATE`, `SELECT FOR NO KEY UPDATE`를 막지만, 다른 `FOR SHARE`와 `FOR KEY SHARE`는 허용한다고 설명한다. 프로젝트에서는 읽은 값을 트랜잭션 끝까지 변경/삭제되지 않게 보호하면서, 다른 shared reader는 허용하고 싶은 경우에 제한적으로 사용한다.

### 5.4 FOR KEY SHARE

PostgreSQL은 `FOR KEY SHARE`가 더 약한 shared row lock이며, `DELETE`나 key 값을 바꾸는 `UPDATE`는 막지만, 일반 non-key `UPDATE`와 다른 `FOR KEY SHARE`/`FOR SHARE`는 허용한다고 설명한다. 프로젝트에서는 부모 row가 삭제되거나 참조 key가 바뀌지 않도록 보호하는 수준이 필요할 때만 검토한다.

### 5.5 항상 가장 약한 충분 조건을 선택한다

PostgreSQL은 row-level lock mode마다 충돌 관계가 다르다. 따라서 프로젝트 기본 원칙은 “익숙하니 무조건 `FOR UPDATE`”가 아니라, 필요한 보호 수준을 충족하는 가장 약한 row lock을 고르는 것이다. 락 강도가 강할수록 대기와 deadlock 가능성도 커진다.

## 6. NOWAIT, SKIP LOCKED, timeout 기준

### 6.1 기본 wait 전략을 무심코 두지 않는다

PostgreSQL은 conflicting lock이 있으면 row/table lock 요청이 해제될 때까지 기다릴 수 있고, deadlock이 아니면 오래 대기할 수 있다고 설명한다. 또한 `lock_timeout`은 lock 획득을 기다리는 동안만 적용되는 별도 timeout이며, 전역 `postgresql.conf`에서 세션 전체에 거는 것은 권장하지 않는다고 설명한다. 프로젝트에서는 “무한 대기”를 기본값으로 방치하지 않고, API 성격에 따라 `NOWAIT` 또는 제한된 timeout을 검토한다.

### 6.2 NOWAIT는 fail-fast가 필요할 때 사용한다

PostgreSQL은 `NOWAIT`가 락을 즉시 잡지 못하면 기다리지 않고 오류를 반환한다고 설명한다. 따라서 사용자 요청 처리처럼 지금 선점할 수 없으면 즉시 충돌 응답을 주는 것이 맞는 작업에는 `NOWAIT`가 적합하다. 프로젝트에서는 “동시에 하나만 처리해야 하는데 기다리기보다 실패가 낫다”는 경우에만 사용한다.

### 6.3 SKIP LOCKED는 queue-like workload에만 제한한다

PostgreSQL은 `SKIP LOCKED`가 잠긴 row를 건너뛰며, 이 방식이 inconsistent view를 만들기 때문에 general-purpose work에는 적합하지 않지만 queue-like table을 여러 consumer가 처리할 때는 유용할 수 있다고 설명한다. 프로젝트에서도 `SKIP LOCKED`는 작업 큐 선점에만 허용하고, 일반 목록 조회나 관리자 화면에는 금지한다.

### 6.4 JPA jakarta.persistence.lock.timeout은 힌트일 뿐이다

Jakarta Persistence는 `jakarta.persistence.lock.timeout`을 pessimistic locking용 timeout 값(밀리초)으로 정의하지만, hint only라고 설명한다. 즉 provider가 관찰하려고 노력해야 하는 값이지, DB별로 완전히 같은 방식으로 강제된다고 가정하면 안 된다. 프로젝트에서는 JPA hint를 사용하더라도 DB/driver/provider 조합에서 실제 동작을 검증한다.

## 7. JPA / Spring Data JPA 기준

### 7.1 Spring Data JPA의 @Lock은 query method에 lock mode intent를 붙이는 수단이다

Spring Data JPA는 repository query method나 CRUD 재선언 메서드에 `@Lock`을 붙여 `LockModeType`을 적용할 수 있다고 설명한다. 따라서 JPA 계층에서 비관적 락을 사용할 때는 repository method에 의도적으로 선언하고, 일반 조회 메서드에 무심코 넓게 붙이지 않는다.

### 7.2 JPA의 pessimistic lock은 PESSIMISTIC_READ, PESSIMISTIC_WRITE, PESSIMISTIC_FORCE_INCREMENT를 기준으로 해석한다

Jakarta Persistence는 `PESSIMISTIC_READ`, `PESSIMISTIC_WRITE`, `PESSIMISTIC_FORCE_INCREMENT`가 즉시 장기 DB 락을 얻는 모드라고 설명한다. 또한 `PESSIMISTIC_READ`는 다른 트랜잭션의 read를 막지 않으면서 repeatable-read 성격의 보호를 제공하고, `PESSIMISTIC_WRITE`는 update 시도들 사이의 직렬화를 강제할 수 있다고 설명한다. 프로젝트에서는 JPA 레벨에서 기본은 `PESSIMISTIC_WRITE`와 `PESSIMISTIC_READ`만 제한적으로 사용하고, `PESSIMISTIC_FORCE_INCREMENT`는 버전 증가 의미가 분명한 경우에만 예외적으로 검토한다.

### 7.3 비관적 락 실패 예외는 두 종류로 나뉜다

Jakarta Persistence는 pessimistic locking 실패가 transaction-level rollback을 일으키면 `PessimisticLockException`을 던지고, statement-level rollback만 일으키면 `LockTimeoutException`을 던지며 현재 트랜잭션은 rollback 표시되지 않을 수 있다고 설명한다. 따라서 프로젝트에서는 두 예외를 같은 것으로 보지 않고, 특히 `LockTimeoutException`을 “statement 실패”로 다룰지 “유스케이스 실패”로 올릴지 application boundary에서 명확히 정한다.

### 7.4 PessimisticLockScope.EXTENDED는 기본값이 아니다

Jakarta Persistence는 pessimistic locking의 기본 scope가 `NORMAL`이고, `EXTENDED`를 쓰면 join table/collection table에 들어 있는 owned relationship과 element collection까지 lock 범위가 넓어진다고 설명한다. 하지만 이 경우에도 참조된 엔티티 상태 자체가 잠기는 것은 아니고, phantom은 여전히 가능하다고 설명한다. 프로젝트에서는 scope를 넓힌다고 “연관 엔티티 전체를 잠근다”고 오해하지 않으며, `EXTENDED`는 기본 금지다.

## 8. deadlock 기준

### 8.1 deadlock은 DB가 자동 감지하지만, 예방이 더 중요하다

PostgreSQL은 deadlock을 자동으로 감지하고, 관련 트랜잭션 중 하나를 abort해서 다른 쪽이 진행되게 만든다고 설명한다. 하지만 어느 쪽이 죽는지는 예측할 수 없고 의존해서는 안 된다. 따라서 프로젝트 기본 원칙은 항상 같은 순서로 잠그고, 처음부터 필요한 가장 강한 락을 잡는 것이다.

### 8.2 deadlock은 재시도 대상이지만, 부분 SQL만 재실행하지 않는다

PostgreSQL은 deadlock으로 abort된 트랜잭션은 on-the-fly retry로 처리할 수 있다고 설명한다. 프로젝트에서는 deadlock 재시도가 필요하면 유스케이스 전체를 새 트랜잭션에서 다시 수행하고, 중간 SQL 몇 줄만 재실행하는 방식은 금지한다.

## 9. 프로젝트 권장안

### 9.1 기본 선택 순서

프로젝트의 기본 선택 순서는 다음과 같다.

- 제약, `ON CONFLICT`, 조건부 UPDATE, `@Version`으로 해결 가능한지 먼저 본다
- 그래도 “현재 존재하는 특정 row를 선점해야” 한다면 row-level pessimistic lock을 검토한다
- queue consumer라면 `SKIP LOCKED`를 검토하되, 일반 업무 조회에는 쓰지 않는다
- table lock은 migration/DDL/운영 작업 수준이 아니면 기본 금지다

이 순서는 PostgreSQL이 explicit locking을 MVCC의 보완 수단으로 설명하고, `SKIP LOCKED`도 queue-like table에 제한적으로 적합하다고 설명하는 점을 프로젝트 규칙으로 압축한 것이다.

### 9.2 일반 비즈니스 row 수정은 lock보다 원자적 update를 먼저 검토한다

단순 상태 전이, 카운터 차감, 중복 방지는 lock보다 단일 SQL 조건식이 더 작고 명확한 해결책인 경우가 많다. explicit lock은 “선택한 row를 이후 로직 동안 반드시 잡고 있어야 하는” 경우에만 올린다. 이는 PostgreSQL의 MVCC/explicit lock 구분과 `SELECT FOR UPDATE`의 대기 비용을 함께 고려한 best practice다.

### 9.3 join query에서는 OF <alias>를 기본 검토한다

join query에 locking clause를 붙일 때 table list를 생략하면 더 넓은 범위가 잠길 수 있다. 프로젝트에서는 lock 대상이 하나의 aggregate root alias로 명확하면 `FOR UPDATE OF o`처럼 범위를 좁히는 것을 기본으로 한다.

### 9.4 queue claim query는 ORDER BY와 함께 설계한다

PostgreSQL은 `LIMIT` query는 `ORDER BY`가 없으면 예측 가능한 subset을 보장하지 않는다고 설명하고, `SKIP LOCKED`는 lock contention 회피용 queue-like case에 적합하다고 설명한다. 프로젝트에서는 queue claim query에 결정적 `ORDER BY` + `LIMIT` + `SKIP LOCKED` 를 함께 검토하고, `OFFSET`은 쓰지 않는다.

## 10. 문서 경계

이 문서는 명시적 락의 선택 기준과 사용 규칙을 다룬다.

다음 내용은 별도 문서에서 확장한다.

- optimistic locking과 `@Version`
- isolation level 선택
- serialization failure / deadlock retry 정책
- queue/outbox/idempotency 전체 설계
- advisory lock / distributed lock

## 11. 금지 규칙

다음은 기본 금지다.

- 중복 생성, 단순 상태 전이, lost update 문제를 무조건 pessimistic lock으로 해결하는 것
- `LOCK TABLE`을 일반 비즈니스 row 처리에 사용하는 것
- `SKIP LOCKED`를 일반 목록 API나 관리자 검색 화면에 사용하는 것
- `OFFSET`과 row lock을 함께 써서 queue claim 범위를 흐리게 만드는 것
- join query에서 lock 대상 alias를 고려하지 않고 넓게 잠그는 것
- 락을 잡은 뒤 외부 API 호출, 긴 계산, 사용자 입력 대기를 넣는 것
- `PESSIMISTIC_WRITE`를 대량 목록/페이지 조회에 거는 것
- `PessimisticLockException`과 `LockTimeoutException`을 같은 의미로 처리하는 것
- `PessimisticLockScope.EXTENDED`를 연관 엔티티 전체 잠금으로 오해하는 것

이 금지 규칙은 PostgreSQL locking clause, explicit locking, timeout, deadlock 문서와 Jakarta Persistence의 pessimistic lock/timeout/scope 규정을 실무 운영 기준으로 요약한 것이다.

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 문제는 정말 explicit lock이 필요한가?
- row lock이면 충분한데 table lock으로 올리지 않았는가?
- `FOR UPDATE`보다 약한 lock mode로 충분하지 않은가?
- join query라면 `OF <alias>`로 lock 대상을 좁혔는가?
- 사용자 요청이라면 `NOWAIT` 또는 bounded wait가 더 맞지 않는가?
- `SKIP LOCKED`를 queue-like workload에서만 쓰고 있는가?
- queue claim query에 결정적 `ORDER BY`가 있는가?
- `OFFSET`과 row lock을 함께 쓰고 있지 않은가?
- JPA에서 lock failure 예외 종류를 구분하고 있는가?
- 락을 잡은 트랜잭션이 짧게 끝나는가?
- 여러 row/object를 잠글 때 항상 같은 순서를 지키는가?
