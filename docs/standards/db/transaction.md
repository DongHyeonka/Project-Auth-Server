# DB Transaction 기준

## 1. 목적

이 문서는 Spring + JPA/Hibernate + PostgreSQL 환경에서 트랜잭션을 어떤 기준으로 시작하고 끝낼지 정의한다.

이 문서의 목표는 다음과 같다.

- 트랜잭션 경계를 repository 호출 단위가 아니라 use case의 일관성 경계로 잡는다
- @Transactional의 기본 의미, rollback 규칙, readOnly 의미를 정확히 해석한다
- long-running transaction, self-invocation, REQUIRES_NEW 남용 같은 신뢰도 낮은 패턴을 줄인다
- 실제 DB 트랜잭션과 JPA persistence context의 관계를 혼동하지 않게 만든다

Spring Data JPA는 보통 여러 repository 호출을 묶는 facade/service가 비-CRUD 작업의 transactional boundary를 정의한다고 설명하고, Hibernate는 물리적 DB 트랜잭션은 가능한 한 짧아야 한다고 설명한다.

## 2. 근거 수준

- Official: Spring Framework / Spring Data JPA / Hibernate / PostgreSQL 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 Spring Framework의 @Transactional, rollback rules, propagation semantics, Spring Data JPA의 transaction boundary guidance, Hibernate의 transaction / flush 문서, PostgreSQL의 transaction isolation 문서를 기준으로 작성한다.

## 3. 기본 원칙

### 3.1 트랜잭션은 “코드 묶음”이 아니라 “일관성 경계”다

트랜잭션은 여러 repository 메서드를 그냥 감싸는 편의 기능이 아니라, 하나의 use case가 모두 반영되거나 모두 취소되어야 하는 일관성 경계를 표현해야 한다. Spring Data JPA도 여러 repository 호출을 묶는 facade/service가 transactional boundary를 정의한다고 설명한다.

### 3.2 물리적 DB 트랜잭션은 가능한 짧게 유지한다

Hibernate는 DB 트랜잭션은 lock contention을 줄이기 위해 가능한 한 짧아야 하고, end-user think time 동안 열어 두지 말라고 설명한다. 또한 Spring의 imperative @Transactional은 현재 실행 스레드에 바인딩되며, 새로 시작한 스레드로는 전파되지 않는다. 프로젝트 기본 원칙은 “트랜잭션은 짧고, 한 스레드 안에서, 필요한 DB 작업만 감싼다”이다.

### 3.3 트랜잭션은 애플리케이션 서비스 메서드에서 시작하는 것을 기본으로 한다

Spring Data JPA는 transaction boundary를 non-CRUD 작업의 시작점, 즉 facade/service 쪽에서 선언하는 방식을 권장한다. 따라서 프로젝트 기본값은 controller도 아니고 repository도 아니라 application service / use case 메서드에서 트랜잭션을 선언하는 것이다.

## 4. 트랜잭션 경계 위치 기준

### 4.1 기본 위치는 application service / use case 메서드

하나의 유스케이스가 여러 repository, domain operation, event publication 준비를 묶는다면 그 외곽 application service 메서드가 트랜잭션 경계가 된다. Spring Data JPA도 여러 repository 호출을 묶는 facade/service가 transactional boundary를 정의한다고 설명한다.

프로젝트 규칙:

- command use case는 application service 메서드에 @Transactional
- 복수 repository를 묶는 read use case도 필요하면 service 메서드에 @Transactional(readOnly = true)
- repository는 데이터 접근 구현 세부이며, 외곽 일관성 경계의 기본 소유자가 아니다

### 4.2 controller에 트랜잭션을 두지 않는다

Hibernate는 물리적 트랜잭션을 짧게 유지하라고 설명하고, Spring은 트랜잭션이 thread-bound execution 안에서 동작한다고 설명한다. controller에 트랜잭션을 두면 request parsing, 외부 호출, response mapping까지 DB 트랜잭션이 불필요하게 길어지기 쉽다. 프로젝트 기본값은 controller에서 입력을 해석하고, service가 트랜잭션을 시작하는 구조다.

### 4.3 domain model 내부에서 트랜잭션을 시작하지 않는다

Spring의 선언적 트랜잭션은 AOP proxy 기반이며, 서비스 계층 객체에 적용되는 것이 일반적이다. transaction boundary는 인프라 관심사이므로 entity / value object / domain service가 직접 시작점이 되지 않게 한다. 이는 Spring의 proxy 기반 transaction model과 계층 분리에 맞는 프로젝트 권장안이다.

## 5. @Transactional 적용 기준

### 5.1 기본 propagation은 REQUIRED

Spring은 PROPAGATION_REQUIRED가 현재 스레드의 공통적인 call stack arrangement에서 좋은 기본값이며, outer service-level transaction에 참여한다고 설명한다. 프로젝트 기본 propagation도 REQUIRED다. 같은 유스케이스 안에서 호출되는 내부 service/repository는 기본적으로 하나의 물리 트랜잭션에 참여한다고 본다.

### 5.2 isolation / timeout 선언은 “새로 시작하는 트랜잭션”에서만 의미가 있다

Spring @Transactional javadoc은 isolation과 timeout이 REQUIRED 또는 REQUIRES_NEW처럼 새로 시작된 트랜잭션에만 적용된다고 설명한다. 기존 트랜잭션에 참여하는 inner scope에서는 outer scope의 특성을 따르며, 기본적으로 local declaration이 조용히 무시될 수 있다.

프로젝트 규칙:

- isolation/timeout은 outer use case 메서드에서 선언하는 것을 기본으로 한다
- inner helper 메서드에서 isolation/timeout을 바꿔도 실제로 반영되지 않을 수 있음을 전제로 한다
- 상세 기준은 별도 isolation.md, lock.md에서 다룬다

### 5.3 REQUIRES_NEW는 독립 커밋이 정말 필요할 때만 사용한다

Spring은 REQUIRES_NEW가 항상 독립적인 물리 트랜잭션을 만들고, outer transaction과 무관하게 commit/rollback 되며, inner transaction의 lock도 완료 시 즉시 해제된다고 설명한다. 따라서 REQUIRES_NEW는 단순 편의 옵션이 아니라 “부분 커밋을 허용하겠다”는 강한 의미다.

프로젝트 규칙:

- 기본값은 REQUIRED
- REQUIRES_NEW는 outer rollback과 무관하게 남아야 하는 audit 기록, 독립 보상 기록 등 정말 독립 커밋이 필요한 경우에만 예외적으로 허용
- “현재 트랜잭션과 충돌하니 일단 REQUIRES_NEW” 같은 사용은 금지한다

## 6. read/write use case 기준

### 6.1 쓰기 use case는 기본적으로 @Transactional

여러 row 변경, aggregate 변경, domain event 적재, 상태 전이 같은 쓰기 use case는 기본적으로 하나의 트랜잭션 안에서 처리해야 한다. Spring Data JPA가 service/facade에서 transactional boundary를 정의하라고 권장하는 이유도 이런 일관성 단위를 서비스 메서드에서 닫기 위해서다.

### 6.2 읽기 use case는 필요 시 @Transactional(readOnly = true)

Spring의 readOnly는 최적화 힌트이며, Spring Data JPA는 대부분의 query method는 읽기이므로 readOnly=true가 일반적이라고 설명한다. 또한 Hibernate와 함께 쓰면 flush mode를 NEVER로 두어 dirty check를 건너뛰는 최적화가 가능하다고 설명한다.

프로젝트 규칙:

- 단순 read query는 readOnly=true를 기본 검토
- 여러 query를 묶거나, lazy association 초기화가 service 안에서 필요하거나, read model 조립이 필요한 경우 service 메서드에 @Transactional(readOnly = true)
- 상세 조회/목록 조회의 fetch plan은 별도 fetch/N+1 문서 기준을 따른다

### 6.3 readOnly=true는 쓰기 차단 장치가 아니다

Spring @Transactional javadoc은 readOnly가 실제 트랜잭션 서브시스템에 대한 힌트일 뿐이며, 쓰기 시도를 반드시 실패시키는 것은 아니라고 설명한다. Spring Data JPA도 readOnly는 조작 query를 막는 검사 장치가 아니라고 명시한다.

프로젝트 규칙:

- readOnly=true를 “절대 write 불가”라고 해석하지 않는다
- readOnly 트랜잭션 안에서 managed entity를 수정하는 코드를 금지한다
- write를 막고 싶다면 설계를 분리하고, 코드 리뷰로 read path와 write path를 명확히 구분한다

## 7. rollback 기준

### 7.1 기본 rollback 규칙을 정확히 이해한다

Spring은 기본적으로 RuntimeException과 Error에서만 rollback하고, checked exception은 기본 설정에서 rollback하지 않는다고 설명한다. 또한 Spring이 권장하는 rollback 신호는 현재 트랜잭션 안에서 예외를 던지는 것이라고 설명한다.

프로젝트 규칙:

- unchecked exception이면 기본 rollback
- checked exception은 기본적으로 rollback되지 않음을 전제로 한다
- checked exception이 곧 유스케이스 실패라면 rollbackFor를 명시하거나 예외 계층을 재설계한다

### 7.2 rollbackFor = Exception.class를 기본값으로 두지 않는다

Spring은 rollback 규칙을 세밀하게 지정할 수 있다고 설명하지만, 기본 규칙은 unchecked exception 기반이다. 모든 checked exception까지 일괄 rollback 대상으로 바꾸면 “복구 가능한 예외”와 “트랜잭션 자체를 취소해야 하는 예외” 구분이 흐려진다. 이 문서의 프로젝트 권장안은 정말 rollback이 필요한 checked exception만 좁게 지정하는 것이다.

### 7.3 rollback 여부는 예외 의미와 맞아야 한다

트랜잭션 rollback 규칙은 기술 설정이 아니라 business outcome을 반영해야 한다. “이 예외가 발생하면 지금까지의 상태 변경을 모두 되돌려야 하는가?”를 기준으로 정해야 한다. Spring이 rollback rules를 예외 타입별로 선언적으로 제어하게 한 이유도 그 의미를 코드 밖에서 명확히 표현하기 위해서다.

## 8. flush / commit 기준

### 8.1 flush와 commit을 같은 것으로 보지 않는다

Hibernate는 flush를 “persistence context 상태를 DB와 동기화하는 과정”으로 정의하고, 변경사항은 먼저 메모리에 반영된 뒤 flush 시 INSERT/UPDATE/DELETE SQL로 변환된다고 설명한다. commit은 flush 이후 실제 트랜잭션 완료와 durability까지 포함하는 더 큰 경계다.

### 8.2 SQL은 commit 직전에만 나간다고 가정하지 않는다

Hibernate는 기본 AUTO flush 모드에서 flush가 transaction commit 직전뿐 아니라, queued entity action과 겹치는 JPQL/HQL query 실행 전, 그리고 EntityManager의 native query 실행 전에도 발생할 수 있다고 설명한다. 따라서 “아직 commit 안 했으니 SQL도 안 나갔을 것”이라는 가정은 안전하지 않다.

프로젝트 규칙:

- 같은 트랜잭션 안에서 조회 query가 flush를 유발할 수 있음을 전제로 한다
- write use case 중간에 불필요한 query를 많이 넣지 않는다
- flush timing에 의존하는 로직보다 명시적 순서와 명확한 트랜잭션 설계를 우선한다

## 9. PostgreSQL 관점의 기본 해석

### 9.1 기본 isolation은 PostgreSQL 기본값을 따른다

Spring @Transactional의 isolation 기본값은 Isolation.DEFAULT이고, PostgreSQL의 기본 isolation level은 Read Committed다. PostgreSQL은 Read Uncommitted를 요청해도 내부적으로 Read Committed처럼 동작한다고 설명한다.

### 9.2 Read Committed에서는 한 트랜잭션 안의 두 SELECT가 서로 다른 결과를 볼 수 있다

PostgreSQL은 Read Committed에서 각 SELECT가 query 시작 시점의 snapshot을 보기 때문에, 같은 트랜잭션 안에서도 두 번의 SELECT가 서로 다른 결과를 볼 수 있다고 설명한다. 따라서 “한 트랜잭션 안이면 읽기 결과가 항상 고정된다”는 가정은 틀릴 수 있다. 상세 기준은 별도 isolation 문서에서 다룬다.

프로젝트 규칙:

- 일반 서비스 기본값은 DB 기본 isolation을 따른다
- stronger isolation이 정말 필요할 때만 명시적으로 올린다
- isolation 문제를 transaction 길이나 REQUIRES_NEW 남용으로 우회하지 않는다

## 10. 프록시 / self-invocation 기준

### 10.1 @Transactional은 프록시를 통과한 외부 호출에서만 기본적으로 동작한다

Spring 공식 문서는 proxy mode가 기본이고, proxy를 통과하는 external method call만 interception 대상이라고 설명한다. 따라서 같은 클래스 안의 self-invocation은 호출된 메서드에 @Transactional이 있어도 실제 트랜잭션이 적용되지 않는다.

프로젝트 규칙:

- transactional method는 같은 클래스 내부에서 자기 자신이 호출하는 구조로 설계하지 않는다
- helper 분리가 필요하면 별도 bean으로 분리하거나 outer service에서 경계를 다시 설계한다
- @PostConstruct 같은 초기화 코드에서 트랜잭션을 기대하지 않는다

### 10.2 트랜잭션 애노테이션은 구체 클래스 메서드에 두는 것을 기본으로 한다

Spring은 구체 클래스 메서드에 @Transactional을 두는 것을 권장하고, interface 선언에만 의존하면 AspectJ mode 등에서 무시될 수 있다고 설명한다. 또한 proxy mode에서는 보통 public method 중심으로 사용하는 것이 자연스럽다.

프로젝트 규칙:

- 기본은 concrete service class의 public method에 @Transactional
- interface에만 선언해 두고 동작을 기대하지 않는다
- method visibility와 proxy 종류 차이를 이해하지 못한 채 비공개 메서드에 남용하지 않는다

## 11. 외부 호출 / 비동기 / after-commit 기준

### 11.1 트랜잭션 안에서 원격 호출이나 오래 걸리는 작업을 길게 잡지 않는다

Hibernate는 DB 트랜잭션을 길게 유지하지 말라고 설명하고, Spring은 imperative 트랜잭션이 현재 스레드에만 바인딩되며 새 스레드에는 전파되지 않는다고 설명한다. 또한 Spring은 transaction context가 remote call로 전파되지 않는다고 설명한다. 따라서 프로젝트 기본값은 DB 작업과 외부 네트워크 호출을 무분별하게 한 트랜잭션 안에 길게 묶지 않는 것이다.

프로젝트 규칙:

- 외부 HTTP/API 호출, 메일 발송, 파일 업로드, 오래 걸리는 연산을 DB 트랜잭션 안에 오래 물고 있지 않는다
- 새 스레드나 @Async성 작업이 같은 트랜잭션에 참여할 것이라고 기대하지 않는다
- 외부 연동 결과 반영은 별도 integration / outbox 기준과 함께 설계한다

### 11.2 commit 이후에만 일어나야 하는 후속 작업은 after-commit에 연결한다

Spring은 @TransactionalEventListener가 AFTER_COMMIT, AFTER_ROLLBACK, AFTER_COMPLETION 같은 phase를 지원한다고 설명한다. 프로젝트에서는 “DB commit이 성공한 뒤에만 발행되어야 하는 후속 처리”는 inline side effect로 섞기보다 after-commit 시점과 연결하는 것을 기본 검토한다.

## 12. 문서 경계

이 문서는 트랜잭션 경계, rollback, readOnly, propagation의 기본 해석을 다룬다.

다음 내용은 별도 문서에서 확장한다.

- isolation level 상세
- optimistic/pessimistic locking
- 동시성 충돌 처리
- outbox / transactional event / integration retry 설계
- migration 실행 트랜잭션 정책

## 13. 금지 규칙

다음은 기본 금지다.

- controller에 @Transactional을 두는 것
- repository 개별 메서드가 outer use case boundary를 소유하게 두는 것
- readOnly=true를 쓰기 방지 장치처럼 오해하는 것
- rollbackFor = Exception.class를 습관적으로 선언하는 것
- helper 메서드 충돌을 피하려고 무심코 REQUIRES_NEW를 붙이는 것
- self-invocation 구조에서 @Transactional이 동작할 것이라 기대하는 것
- 트랜잭션 안에서 새 스레드를 시작하고 같은 트랜잭션 참여를 기대하는 것
- long-running external call을 DB 트랜잭션 안에 오래 포함시키는 것
- flush와 commit을 같은 것으로 보는 것

이 금지 규칙은 Spring의 proxy/rollback/propagation semantics, Hibernate의 short transaction 및 flush semantics, PostgreSQL의 isolation 기본 동작을 실무 운영 기준으로 압축한 것이다.

## 14. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 메서드는 하나의 use case 일관성 경계를 대표하는가?
- 트랜잭션이 controller가 아니라 application service에서 시작되는가?
- transaction scope가 불필요하게 길지 않은가?
- readOnly=true를 최적화 힌트로만 해석하고 있는가?
- checked exception rollback 여부를 명시적으로 판단했는가?
- REQUIRES_NEW가 정말 독립 커밋 의미와 맞는가?
- flush가 commit 전에 일어날 수 있음을 고려했는가?
- self-invocation 때문에 @Transactional이 무시되지 않는가?
- 새 스레드/비동기 작업이 같은 트랜잭션에 참여한다고 가정하지 않는가?
- isolation/lock 요구를 transaction boundary 문제와 분리해서 생각하고 있는가?
