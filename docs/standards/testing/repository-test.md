# Repository Test 기준

## 1. 목적

이 문서는 Spring Data JPA 기반 repository test를 어떤 범위까지 검증하고, 어떤 방식으로 실행할지 정의한다.

이 문서의 목표는 다음과 같다.

- repository test의 관심사를 영속성 경계로 한정한다
- 기본 실행 방식으로 `@DataJpaTest`를 사용하고, `@SpringBootTest`와 역할을 분리한다
- 영속성 컨텍스트 1차 캐시 때문에 가짜로 통과하는 테스트를 줄인다
- PostgreSQL 특화 query나 제약 검증이 필요한 경우, 실제 DB 계열과 맞는 환경에서 검증하게 만든다

Spring Boot는 `@DataJpaTest`가 JPA components에만 초점을 맞춘 테스트이며, 기본적으로 `@Entity`와 Spring Data JPA repository를 스캔하고, 일반 `@Component` 빈은 로드하지 않는다고 설명한다. 또한 임베디드 DB가 classpath에 있으면 그것을 자동 구성하고, 기본적으로 transactional하게 실행된다고 설명한다.

## 2. 근거 수준

- Official: Spring Boot / Spring Data JPA / Spring Framework / Hibernate 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 `@DataJpaTest`, `TestEntityManager`, Spring 테스트 트랜잭션 rollback, Hibernate 1차 캐시/영속성 컨텍스트 문서를 기준으로 작성한다. Spring Data JPA는 repository query method의 transaction 설정 규칙도 별도로 설명하고 있다.

## 3. 기본 원칙

### 3.1 repository test는 repository 경계만 검증한다

repository test의 관심사는 서비스 유스케이스가 아니라 엔티티 매핑, repository method, JPQL/native query, flush 시점의 제약 위반, DB round-trip 이후의 조회 결과다. `@DataJpaTest`가 JPA components만 좁게 로드하도록 설계된 것도 이 경계를 전제로 한다. 프로젝트에서는 service orchestration, 외부 연동, 보안, MVC, application event 흐름은 repository test에 넣지 않는다.

### 3.2 repository test의 기본값은 @DataJpaTest

Spring Boot는 `@DataJpaTest`가 Data JPA 테스트에 필요한 auto-configuration만 켜고, 엔티티와 repository만 중심으로 로드한다고 설명한다. 따라서 repository test의 기본 어노테이션은 `@DataJpaTest`다. `@SpringBootTest`는 전체 애플리케이션 조립이 필요한 경우에만 예외적으로 사용하고, repository test의 기본값으로 두지 않는다.

### 3.3 repository test는 “메서드가 호출된다”가 아니라 “DB 의미가 맞다”를 검증해야 한다

JPA/Hibernate는 영속성 컨텍스트를 1차 캐시로 유지하며, Hibernate는 이를 generally “repeatable read” persistence context라고 설명한다. 따라서 같은 트랜잭션 안에서 entity를 다시 읽을 때, 실제 DB round-trip 없이 메모리 상태만 보게 될 수 있다. 프로젝트에서는 repository test가 정말 DB 의미를 검증해야 한다면, 필요 지점에서 `flush()`와 `clear()`를 사용해 영속성 컨텍스트 환상을 걷어낸 뒤 검증하는 것을 기본 원칙으로 둔다.

## 4. 언제 repository test를 작성하는가

### 4.1 다음은 repository test의 대표 대상이다

repository test의 대표 대상은 다음과 같다.

- 엔티티 매핑이 실제 스키마와 맞는지
- derived query method가 기대한 조건으로 동작하는지
- JPQL/native query가 기대한 결과를 반환하는지
- unique/fk/check/not null 같은 DB 제약이 flush 시점에 올바르게 드러나는지
- soft delete, partial index 전제 predicate, 정렬/페이지 query가 의도대로 동작하는지

이들은 모두 JPA repository/EntityManager 경계의 책임이며, `@DataJpaTest`가 좁게 검증하기에 적합한 주제다.

### 4.2 다음은 repository test의 기본 대상이 아니다

다음은 repository test의 기본 대상이 아니다.

- 서비스 유스케이스 전체 흐름
- 여러 repository를 묶는 트랜잭션 정책
- 보안 컨텍스트와 인증 인가
- MVC 요청/응답 변환
- 외부 API 연동과 메시징 흐름

이런 항목은 `@SpringBootTest`, slice test, 혹은 더 상위 통합 테스트의 관심사다. `@DataJpaTest`가 일반 `@Component`를 로드하지 않는다는 점도 이 구분과 맞는다.

## 5. 기본 실행 방식 기준

### 5.1 기본 어노테이션은 @DataJpaTest

Spring Boot는 `@DataJpaTest`가 JPA test에 초점을 맞추고, 엔티티와 repository를 스캔하며, 임베디드 DB가 있으면 그것을 자동 구성한다고 설명한다. 또한 기본적으로 transactional하게 실행되고, Spring 테스트 트랜잭션은 종료 시 기본 rollback된다. 프로젝트에서는 repository test 클래스의 기본 시작점을 `@DataJpaTest`로 둔다.

### 5.2 임베디드 DB 기본값을 무심코 신뢰하지 않는다

`@DataJpaTest`는 기본적으로 임베디드 DB를 구성할 수 있다. 하지만 repository가 PostgreSQL dialect, native query, JSONB, partial index, window function, locking clause, case sensitivity 차이 같은 DB 고유 동작에 의존한다면, 임베디드 DB만으로는 신뢰도가 부족할 수 있다. Spring Boot는 실제 DB를 선호하면 `@AutoConfigureTestDatabase`로 대체 전략을 제어할 수 있다고 설명한다. 프로젝트에서는 DB 특화 기능이 있는 repository test는 실제 운영 DB 계열로 검증하는 것을 기본 권장안으로 둔다.

### 5.3 @SpringBootTest는 repository test의 예외 경로다

repository 자체는 `@DataJpaTest`로 충분한 경우가 대부분이다. 다만 repository가 Boot auto-configuration, custom converter, listener, 여러 인프라 bean과 강하게 얽혀 있고 그 조합 자체를 검증해야 한다면 예외적으로 `@SpringBootTest`를 사용할 수 있다. 하지만 이 경우도 관심사는 여전히 repository 경계여야 하며, 단순히 편하다는 이유로 full context를 올리지는 않는다. 이 기준은 `@DataJpaTest`의 공식 역할 위에 얹는 프로젝트 best practice다.

## 6. 트랜잭션 기준

### 6.1 repository test는 기본적으로 rollback된다

Spring 테스트 문서는 transactional test가 기본적으로 종료 후 rollback된다고 설명한다. Spring Boot 문서도 `@DataJpaTest`가 기본적으로 transactional하게 동작한다고 설명한다. 따라서 repository test는 기본적으로 test isolation을 위해 rollback을 기대할 수 있다.

### 6.2 commit이 필요한 테스트만 예외적으로 @Commit 또는 @Rollback(false)를 사용한다

Spring은 `@Rollback(false)` 또는 `@Commit`으로 테스트 트랜잭션을 commit하도록 바꿀 수 있다고 설명한다. 프로젝트에서는 DB trigger, 외부 관측, 별도 세션에서만 보이는 결과, commit 이후 동작을 검증해야 할 때만 예외적으로 commit 테스트를 허용한다. 기본값은 rollback이다.

### 6.3 repository query method 자체의 transaction 설정을 혼동하지 않는다

Spring Data JPA는 declared query methods와 default methods에는 transaction configuration이 기본 적용되지 않으며, 필요하면 repository interface에 `@Transactional`을 명시해야 한다고 설명한다. 다만 `@DataJpaTest` 안에서는 테스트 메서드 자체가 트랜잭션 안에서 실행되므로, repository query method의 transaction 유무와 테스트 트랜잭션의 존재를 혼동하면 안 된다. 프로젝트에서는 repository method의 production transaction semantics를 검증하려는 테스트라면, 테스트 메서드 트랜잭션에 가려지지 않는지 먼저 확인한다.

## 7. 검증 방식 기준

### 7.1 저장 직후 검증이 아니라 flush() 이후 의미를 본다

영속성 컨텍스트 안에서 `save()`만 호출하고 바로 필드를 확인하면, 실제 SQL 실행이나 DB 제약 위반이 드러나지 않을 수 있다. Hibernate는 persistence context가 1차 캐시로 동작한다고 설명한다. 따라서 unique/fk/check/not null 위반, DB generated value, trigger, native query 결과를 보려면 `flush()`를 통해 DB와 동기화한 뒤 검증하는 것을 기본으로 한다.

### 7.2 조회 의미를 검증할 때는 필요하면 clear()까지 사용한다

같은 persistence context 안에서는 이미 읽은 엔티티가 다시 반환될 수 있다. 따라서 repository test가 정말 DB round-trip 이후의 조회 semantics를 보고 싶다면, `flush()` 후 `clear()`를 통해 1차 캐시를 비우고 다시 조회해야 한다. 프로젝트에서는 “쿼리가 실제로 원하는 row를 다시 읽어오는가”를 검증할 때 `clear()`를 적극적으로 사용한다.

### 7.3 예외 검증은 가능한 한 flush 시점까지 진행한다

제약 위반은 보통 DB에 SQL이 나가야 드러난다. 따라서 repository test에서 `assertThatThrownBy(() -> repository.save(entity))`처럼 save 호출만 감싸는 패턴은 충분하지 않을 수 있다. 프로젝트에서는 제약/매핑 오류 테스트를 `save` + `flush` 또는 `persistAndFlush` 수준까지 진행한 뒤 검증하는 것을 기본값으로 둔다. 이 규칙은 JPA flush semantics와 1차 캐시 특성을 근거로 한 best practice다.

## 8. TestEntityManager 기준

### 8.1 TestEntityManager는 repository test 보조 도구로 허용한다

Spring Boot는 `TestEntityManager`를 JPA 테스트용 대안 `EntityManager`로 제공하며, `persist`, `flush`, `find` 같은 testing helper를 제공한다고 설명한다. 프로젝트에서는 fixture seed, flush/clear, ID 확보, 영속성 컨텍스트 제어가 자주 필요할 때 `TestEntityManager` 사용을 허용한다.

### 8.2 다만 repository test의 중심은 여전히 repository여야 한다

`TestEntityManager`는 보조 도구이지 테스트 대상이 아니다. repository method를 검증하는 테스트가 `EntityManager` 호출로 가득 차면, 결국 repository를 우회한 테스트가 되기 쉽다. 프로젝트에서는 seed와 보조 검증 정도에만 쓰고, 핵심 assertion은 repository method 결과에 두는 것을 원칙으로 한다. 이 부분은 공식 API 역할 설명 위에 얹는 프로젝트 best practice다.

## 9. 데이터 준비 기준

### 9.1 repository test 데이터는 테스트 의도에 필요한 최소한만 준비한다

repository test는 SQL semantics를 검증하는 테스트이므로, 데이터가 많다고 좋은 것이 아니다. 정렬, 필터, unique, soft delete, join 조건을 드러내는 최소 사례 집합이 가장 좋다. 이 기준은 공식 문서가 직접 규정하는 항목은 아니지만, `@DataJpaTest`의 좁은 목적과 빠른 피드백에 맞는 실무 best practice다.

### 9.2 테스트마다 필요한 데이터는 독립적으로 준비한다

기본 rollback이 되더라도, 테스트끼리 순서 의존적인 데이터 준비를 하면 의도가 흐려진다. 프로젝트에서는 각 테스트가 자기 전제 데이터를 스스로 준비하게 하고, 외부 상태나 이전 테스트의 삽입 결과에 의존하지 않게 한다. 이는 Spring 테스트의 기본 rollback 모델과 맞는 프로젝트 규칙이다.

## 10. 무엇을 검증해야 하는가

### 10.1 repository test는 아래 항목을 우선 검증한다

프로젝트 기준으로 repository test가 특히 잘 검증해야 하는 것은 다음과 같다.

- 엔티티와 테이블/컬럼 매핑
- 연관관계 매핑과 cascade로 인해 실제 SQL이 기대대로 나가는지
- derived query method의 조건 해석
- JPQL/native query의 결과 정확성
- soft delete predicate, 정렬, pagination query
- partial unique index, FK, check, not null 같은 DB 무결성 위반 드러남
- flush 이후 다시 조회했을 때도 상태가 맞는지

이 항목들은 모두 영속성 경계의 책임이며, repository test에 가장 적합하다.

### 10.2 반대로 service policy는 repository test에서 검증하지 않는다

예외 변환, 유스케이스 조합, 외부 연동과 결합된 정책은 repository test에서 검증하지 않는다. 그런 항목은 상위 통합 테스트의 책임이다. repository test가 이 범위를 침범하면 `@DataJpaTest`의 좁은 장점이 사라진다.

## 11. 프로젝트 권장안

### 11.1 repository test의 기본 템플릿

프로젝트의 기본 repository test 템플릿은 다음과 같다.

- `@DataJpaTest`
- 필요 시 실제 DB 계열 사용
- fixture 준비
- `repository.save(...)`
- 필요 시 `flush()`, `clear()`
- repository로 다시 조회
- DB 의미 기준 assertion

이 흐름은 Spring Boot의 JPA slice test와 Hibernate 1차 캐시 특성을 함께 고려한 프로젝트 기본값이다.

### 11.2 PostgreSQL 의존 쿼리는 PostgreSQL로 검증한다

partial index 전제, JSONB, native query, locking clause, window function, case sensitivity, timestamp handling처럼 PostgreSQL 의미에 의존하는 repository test는 임베디드 대체 DB보다 실제 PostgreSQL 계열 환경에서 검증하는 것을 기본 권장안으로 둔다. Spring Boot가 실제 DB 사용을 위한 `@AutoConfigureTestDatabase` 제어를 제공하는 점과도 맞다.

## 12. 금지 규칙

다음은 기본 금지다.

- repository test 기본값으로 `@SpringBootTest` 사용
- 순수 서비스 정책 테스트를 repository test에 넣는 것
- `save()` 직후 영속성 컨텍스트 상태만 보고 DB 검증이 끝났다고 생각하는 것
- 제약 위반 테스트를 flush 없이 작성하는 것
- 1차 캐시 때문에 다시 읽은 엔티티를 실제 DB 조회 결과로 오해하는 것
- PostgreSQL 특화 query를 임베디드 DB만으로 신뢰하는 것
- soft delete, 정렬, pagination query를 최소 데이터셋 없이 대충 검증하는 것
- bulk data seed나 복잡한 service 조립을 repository test에 끌어오는 것
- repository test에서 repository를 거의 쓰지 않고 `EntityManager`만 사용하는 것

이 금지 규칙은 Spring Boot의 `@DataJpaTest`, Spring 테스트 rollback, Hibernate persistence context 의미를 실무 규칙으로 압축한 것이다.

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 테스트는 repository 경계만 검증하고 있는가?
- 기본 어노테이션이 `@DataJpaTest`인가?
- 실제 운영 DB 의미가 중요하면 테스트 DB도 그에 맞췄는가?
- DB 제약/trigger/generated value를 검증할 때 `flush()`를 사용했는가?
- 실제 재조회 semantics를 검증할 때 `clear()`까지 고려했는가?
- 테스트 데이터가 최소하지만 충분한가?
- repository method 결과를 중심으로 assertion하고 있는가?
- service 정책이나 웹 계층 검증이 섞이지 않았는가?
