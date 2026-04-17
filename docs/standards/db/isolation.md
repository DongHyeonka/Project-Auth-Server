# Transaction Isolation 기준

## 1. 목적

이 문서는 PostgreSQL과 Spring 트랜잭션 환경에서 트랜잭션 격리수준(isolation level) 을 어떤 기준으로 선택할지 정의한다.

이 문서의 목표는 다음과 같다.

- `READ COMMITTED`, `REPEATABLE READ`, `SERIALIZABLE`의 PostgreSQL 실제 동작을 기준으로 해석한다
- Spring `Isolation` enum의 일반적 설명과 PostgreSQL 구현 차이를 구분한다
- 격리수준을 “성능 문제 생기면 일단 올리는 옵션”이 아니라 증명해야 하는 정합성 요구로 다룬다
- stronger isolation이 필요할 때도 재시도 정책까지 포함해서 설계하게 만든다

PostgreSQL은 SQL 표준의 네 가지 isolation level을 요청할 수 있지만, 내부적으로는 세 가지 distinct level만 구현하며, `READ UNCOMMITTED`는 `READ COMMITTED`처럼 동작한다고 설명한다. 또한 PostgreSQL의 `REPEATABLE READ`는 phantom read도 허용하지 않는다고 설명한다.

## 2. 근거 수준

- Official: PostgreSQL / Spring Framework 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL의 Transaction Isolation, `SET TRANSACTION`, Serialization Failure Handling 문서와 Spring Framework의 `@Transactional` / `Isolation` 문서를 기준으로 작성한다. Spring은 `@Transactional` 기본 isolation이 `ISOLATION_DEFAULT`라고 설명하고, PostgreSQL은 기본 isolation이 보통 `READ COMMITTED`라고 설명한다.

## 3. 기본 원칙

### 3.1 격리수준은 기본값을 바꾸는 튜닝 옵션이 아니라 정합성 요구다

트랜잭션 격리수준은 “더 안전해 보이니까 올린다”가 아니라, 어떤 이상 현상(anomaly)을 허용할 수 없는가를 기준으로 선택해야 한다. PostgreSQL은 각 isolation level을 dirty read, nonrepeatable read, phantom read, serialization anomaly 관점에서 설명하고 있다. 따라서 선택 기준도 “이 유스케이스가 어떤 이상 현상에 취약한가”여야 한다.

### 3.2 Spring enum 설명보다 PostgreSQL 실제 구현을 source of truth로 둔다

Spring의 `Isolation` enum은 일반적인 JDBC 의미를 설명하며, 예를 들어 `REPEATABLE_READ`는 phantom read가 발생할 수 있다고 기술한다. 하지만 PostgreSQL은 `REPEATABLE READ` 구현이 phantom read를 허용하지 않는다고 명시한다. 따라서 PostgreSQL 기반 프로젝트에서는 Spring enum의 일반 설명을 참고하되, 실제 의미는 PostgreSQL 문서를 기준으로 해석해야 한다.

### 3.3 격리수준을 올렸으면 재시도까지 함께 설계해야 한다

PostgreSQL은 `REPEATABLE READ`와 `SERIALIZABLE` 모두에서 serialization anomaly를 막기 위해 실패가 발생할 수 있고, 애플리케이션은 serialization failure를 재시도할 준비가 되어 있어야 한다고 설명한다. 따라서 stronger isolation을 선택하는 것은 단순 설정 변경이 아니라 실패와 재시도 모델을 도입하는 일이다.

## 4. Spring 기준

### 4.1 기본값은 Isolation.DEFAULT

Spring은 `@Transactional`의 기본 isolation이 `ISOLATION_DEFAULT`라고 설명한다. Spring `Isolation.DEFAULT`는 underlying data store의 기본 isolation level을 사용한다. PostgreSQL의 기본 isolation은 보통 `READ COMMITTED`이므로, 별도 지정이 없으면 대부분의 경우 PostgreSQL 기본값을 따른다.

프로젝트 규칙:

- 기본값은 `Isolation.DEFAULT`
- PostgreSQL 기본값이 곧 프로젝트의 일반 기본값이다
- 특별한 근거 없이 모든 서비스 메서드에 isolation을 명시하지 않는다

### 4.2 isolation 선언은 새 트랜잭션을 시작할 때만 적용된다고 본다

Spring `@Transactional` javadoc은 isolation setting이 `REQUIRED` 또는 `REQUIRES_NEW`처럼 새로 시작된 트랜잭션에만 적용된다고 설명한다. 또한 기존 트랜잭션에 참여하는 inner scope에서는 local isolation declaration이 기본적으로 무시된다고 설명한다.

프로젝트 규칙:

- isolation은 outer use case 메서드에서 선언하는 것을 기본으로 한다
- 내부 helper 메서드에서 isolation만 다르게 선언해도 실제로는 반영되지 않을 수 있다고 본다
- isolation mismatch를 엄격히 다루고 싶다면 Spring transaction manager의 `validateExistingTransactions` 검토를 별도 운영 설정으로 다룬다

## 5. PostgreSQL 격리수준 해석 기준

### 5.1 READ UNCOMMITTED

PostgreSQL은 `READ UNCOMMITTED`를 요청할 수 있지만 내부적으로는 `READ COMMITTED`처럼 동작한다고 설명한다. 이는 PostgreSQL MVCC 구조에서 그 방식이 유일하게 sensible한 매핑이라고 명시한다.

프로젝트 규칙:

- PostgreSQL에서는 `READ_UNCOMMITTED`를 별도 전략으로 사용하지 않는다
- dirty read를 기대하고 `READ_UNCOMMITTED`를 쓰는 것은 금지한다
- `READ_UNCOMMITTED` 요청은 사실상 `READ_COMMITTED`와 같다고 본다

### 5.2 READ COMMITTED

PostgreSQL은 `READ COMMITTED`가 기본 isolation level이며, 각 SELECT가 그 쿼리 시작 시점 기준의 snapshot만 본다고 설명한다. 따라서 같은 트랜잭션 안의 두 SELECT라도, 그 사이 다른 트랜잭션이 commit하면 서로 다른 결과를 볼 수 있다. 또한 concurrent update가 먼저 commit되면 뒤늦게 도착한 update/delete는 갱신된 row 버전에 대해 `WHERE` 조건을 다시 평가할 수 있다고 설명한다.

프로젝트 해석:

- 기본 CRUD, 일반 API, 대부분의 업무 트랜잭션 기본값은 `READ COMMITTED`
- 같은 트랜잭션 안이라도 여러 query가 같은 snapshot을 본다고 가정하지 않는다
- 복잡한 read-check-write나 cross-row invariant 검증에는 기본값만으로 충분한지 별도 검토한다

### 5.3 REPEATABLE READ

PostgreSQL은 `REPEATABLE READ`가 트랜잭션 시작 이후 다른 트랜잭션이 commit한 변화를 보지 않으며, 같은 트랜잭션 안의 successive SELECT가 같은 snapshot을 본다고 설명한다. 또한 PostgreSQL의 구현은 phantom read도 허용하지 않으며, SQL 표준 최소 보장보다 더 강하다고 명시한다. 하지만 serialization anomalies는 여전히 가능하고, 애플리케이션은 실패 재시도를 준비해야 한다고 설명한다.

프로젝트 해석:

- point-in-time read consistency가 필요한 다단계 조회/검증에는 `REPEATABLE READ`를 검토한다
- 하지만 cross-row / cross-predicate invariant가 정말 깨지면 안 되는 유스케이스에는 `SERIALIZABLE`이 더 적절할 수 있다
- `REPEATABLE READ`를 쓴다고 해서 serialization anomaly까지 사라진다고 오해하지 않는다

### 5.4 SERIALIZABLE

PostgreSQL은 `SERIALIZABLE`이 가장 엄격한 isolation이며, 성공적으로 commit한 트랜잭션 집합이 마치 한 번에 하나씩 실행된 것과 같은 효과를 보장한다고 설명한다. 구현은 `REPEATABLE READ`처럼 동작하되, serialization anomaly를 만들 수 있는 read/write dependency를 감시하고 필요하면 한 트랜잭션을 `40001`로 rollback시킨다. PostgreSQL은 이를 위해 predicate locking을 사용하며, 이 lock은 anomaly 감지용이지 blocking을 추가로 만드는 lock은 아니라고 설명한다.

프로젝트 해석:

- cross-row 합계, 범위 기반 제약, 조건부 집합 연산처럼 serial execution과 같은 의미가 필요한 경우에만 `SERIALIZABLE`
- `SERIALIZABLE`을 선택하면 재시도 정책이 설계에 포함되어야 한다
- 단순히 “더 안전하니까” 전체 시스템 기본값으로 올리지 않는다

## 6. isolation 선택 기준

### 6.1 기본값은 READ COMMITTED 해석이다

Spring 기본값은 `DEFAULT`, PostgreSQL 기본값은 보통 `READ COMMITTED`이다. PostgreSQL도 이 수준이 빠르고 단순하며 많은 애플리케이션에 충분하다고 설명한다. 프로젝트 기본값도 동일하다. 일반적인 단건/소규모 CRUD와, DB 제약·원자적 UPDATE·낙관적 락으로 이미 정합성이 닫히는 유스케이스는 `READ COMMITTED`를 기본으로 한다.

### 6.2 같은 트랜잭션 안에서 동일 snapshot이 꼭 필요하면 REPEATABLE READ를 검토한다

보고서 생성, 다단계 조회 기반 검증, point-in-time 정산 미리보기처럼 한 트랜잭션 안에서 여러 query가 같은 세계를 봐야 하는 경우에는 `REPEATABLE READ`를 검토한다. PostgreSQL은 이 수준에서 successive SELECT가 같은 snapshot을 본다고 설명한다.

### 6.3 집합 단위 정합성까지 깨지면 안 되면 SERIALIZABLE을 검토한다

“동시에 두 트랜잭션이 서로 다른 조건을 읽고, 각각 다른 row를 추가/갱신해서 전체 집합 규칙을 깨는” 종류의 문제는 `READ COMMITTED`나 `REPEATABLE READ`만으로는 막지 못할 수 있다. PostgreSQL은 바로 이런 serialization anomaly를 `SERIALIZABLE`이 막는다고 설명한다.

### 6.4 stronger isolation보다 더 직접적인 수단이 있으면 먼저 쓴다

중복 생성은 `UNIQUE`/`ON CONFLICT`, lost update는 `@Version`, 상태 전이는 조건부 UPDATE, 작업 선점은 row lock처럼 더 직접적인 수단이 있을 수 있다. PostgreSQL도 `READ COMMITTED`에서 `ON CONFLICT`와 조건부 UPDATE가 원자적으로 유용하게 동작하는 사례를 설명한다. 프로젝트에서는 isolation level을 올리기 전에 더 작은 수단으로 문제가 닫히는지 먼저 검토한다.

## 7. 재시도 기준

### 7.1 REPEATABLE READ와 SERIALIZABLE은 재시도 준비가 필요하다

PostgreSQL은 `REPEATABLE READ`와 `SERIALIZABLE` 모두 serialization failure를 낼 수 있고, 이때 애플리케이션은 트랜잭션을 재시도할 준비가 되어 있어야 한다고 설명한다. 또한 serialization failure의 SQLSTATE는 `40001`이라고 명시한다.

프로젝트 규칙:

- stronger isolation을 도입하면 `40001` 재시도 정책을 함께 설계한다
- 재시도는 트랜잭션 전체 유스케이스를 새 트랜잭션에서 다시 수행하는 방식으로 한다
- 일부 SQL만 재실행하는 방식은 기본 금지다

### 7.2 SERIALIZABLE read-only deferrable은 예외적 고급 옵션이다

PostgreSQL은 `SERIALIZABLE READ ONLY DEFERRABLE` 트랜잭션이 anomaly-free snapshot을 확보할 때까지 기다릴 수 있고, 이 경우 읽은 데이터는 읽는 즉시 유효하다고 설명한다. 다만 일반 read-only 트랜잭션의 기본값은 아니며, 고정 snapshot 품질이 중요한 특수 보고/검증 시나리오에만 의미가 있다.

프로젝트 규칙:

- 일반 read-only 서비스에 기본 적용하지 않는다
- 장시간 보고서/정산 검증처럼 snapshot 품질이 매우 중요한 특수 작업에서만 검토한다

## 8. PostgreSQL 특이사항 기준

### 8.1 sequence 값은 트랜잭션 rollback과 무관하게 보일 수 있다

PostgreSQL은 sequence 변경이 다른 트랜잭션에 즉시 visible하고, 해당 트랜잭션이 abort되어도 rollback되지 않는다고 설명한다. 따라서 serial/sequence 값의 연속성이나 “rollback되면 번호도 되돌아갈 것” 같은 기대는 isolation과 무관하게 틀릴 수 있다.

프로젝트 규칙:

- sequence/identity 값의 gap 없는 연속성을 비즈니스 의미로 사용하지 않는다
- 번호 연속성 자체가 중요하면 별도 번호 정책으로 다룬다

## 9. 프로젝트 권장안

### 9.1 기본값은 Isolation.DEFAULT + PostgreSQL READ COMMITTED

프로젝트 기본값은 Spring에서 isolation을 생략하고, PostgreSQL 기본 isolation인 `READ COMMITTED`를 따른다. 이는 대부분의 CRUD와 일반 서비스 로직에 충분하며, PostgreSQL도 이 수준이 fast and simple하다고 설명한다.

### 9.2 REPEATABLE READ는 “같은 트랜잭션 안의 안정된 조회 시점”이 필요할 때만

여러 단계 조회가 모두 같은 snapshot을 봐야 하지만, serial equivalence 전체까지는 필요 없는 경우에만 `REPEATABLE READ`를 검토한다. 단, serialization failure 가능성이 있으므로 “읽기 안정화”만 생각하고 재시도를 잊지 않는다.

### 9.3 SERIALIZABLE은 “정말 serial semantics가 필요한 유스케이스”에만

범위 조건 기반 정합성, 집합 단위 규칙, 서로 다른 조건을 읽은 뒤 상호 영향을 주는 write가 있는 경우처럼 실제로 serial execution과 같은 의미가 필요한 곳에만 `SERIALIZABLE`을 사용한다. PostgreSQL은 이 수준이 predicate locking과 dependency tracking을 통해 serialization anomaly를 막는다고 설명한다.

### 9.4 isolation은 기본 해결책이 아니라 마지막 선택지에 가깝다

대부분의 비즈니스 동시성 문제는 `UNIQUE`, `ON CONFLICT`, `@Version`, 조건부 UPDATE, explicit row lock 등 더 직접적인 수단으로 더 명확하게 풀린다. isolation level은 그런 수단으로도 닫히지 않는 snapshot/invariant 문제에 한해 올린다. 이는 PostgreSQL의 MVCC, `READ COMMITTED` update semantics, serialization failure model을 함께 고려한 프로젝트 권장안이다.

## 10. 문서 경계

이 문서는 격리수준의 의미와 선택 기준을 다룬다.

다음 내용은 별도 문서에서 확장한다.

- optimistic locking과 `@Version`
- explicit row lock (`FOR UPDATE`, `NOWAIT`, `SKIP LOCKED`)
- deadlock/serialization failure 재시도 구현
- idempotency / UPSERT / uniqueness 설계
- outbox / queue / worker claim 설계

## 11. 금지 규칙

다음은 기본 금지다.

- PostgreSQL에서 `READ_UNCOMMITTED`를 dirty read 용도로 기대하는 것
- 같은 트랜잭션 안이면 항상 같은 조회 결과를 본다고 가정하는 것
- `REPEATABLE_READ`가 serialization anomaly까지 막는다고 오해하는 것
- `SERIALIZABLE`을 재시도 설계 없이 도입하는 것
- stronger isolation을 전체 서비스의 기본값으로 기계적으로 올리는 것
- isolation으로 해결할 문제가 아닌 uniqueness / lost update / 상태 전이를 격리수준만으로 풀려는 것
- outer transaction이 있는데 inner method isolation 선언이 실제로 override될 것이라고 기대하는 것
- sequence 값이 rollback될 것이라고 기대하는 것

이 금지 규칙은 PostgreSQL의 actual isolation semantics와 Spring transaction 선언 규칙을 실무 운영 기준으로 압축한 것이다.

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 유스케이스는 정말 기본 `READ COMMITTED`로 충분하지 않은가?
- 필요한 것이 stable snapshot인가, serial semantics인가?
- stronger isolation 대신 제약 / 원자적 update / 낙관적 락 / row lock으로 더 직접적으로 풀 수 없는가?
- `REPEATABLE READ`를 쓴다면 같은 snapshot이 필요한 이유를 설명할 수 있는가?
- `SERIALIZABLE`을 쓴다면 어떤 serialization anomaly를 막으려는지 설명할 수 있는가?
- `40001` 재시도 정책이 함께 설계되어 있는가?
- Spring isolation 선언이 실제로 새 트랜잭션에서만 의미 있음을 알고 있는가?
- sequence/identity 값의 특수 동작을 비즈니스 의미로 오해하지 않는가?
