# N+1 기준

## 1. 목적

이 문서는 JPA/Hibernate 환경에서 N+1 query 문제를 어떤 기준으로 정의하고, 어떻게 탐지하고, 어떤 우선순위로 해결할지 정의한다. 이 문서의 목표는 다음과 같다.

- N+1을 단순히 “쿼리가 많다”가 아니라 루트 조회 1번 이후 연관 로딩이 엔티티 수만큼 반복되는 SQL shape 문제로 다룬다.
- EAGER 선언이나 임시 캐시 같은 우회책이 아니라, query/use case 단위의 fetch plan 설계로 해결한다.
- 목록·상세·배치·페이징 상황에 맞는 해결 수단을 구분한다.

Hibernate는 fetch tuning이 전체 성능에 매우 큰 영향을 주며, SELECT 기반 secondary select 전략이 바로 일반적으로 말하는 N+1이라고 설명한다.

## 2. 근거 수준

- Official: Jakarta Persistence / Hibernate / Spring Data JPA 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 Jakarta Persistence 3.2의 fetch semantics, Hibernate ORM User Guide의 fetching / join fetch / batch fetching / pagination over collection fetch / LazyInitializationException guidance, Spring Data JPA의 @EntityGraph 지원 문서를 근거로 한다.

## 3. 정의

### 3.1 N+1은 “루트 1번 + 연관 N번”의 SQL shape 문제다

Hibernate 공식 문서는 SELECT 기반 fetching이 연관 데이터를 별도 SQL로 가져오는 방식이며, 이것이 일반적으로 N+1이라고 불리는 전략이라고 설명한다. 또한 루트 엔티티 여러 건을 먼저 가져온 뒤, 각 엔티티의 연관 컬렉션이나 to-one 연관을 접근할 때마다 secondary select가 반복되면 N+1이 발생한다고 설명한다. 따라서 N+1은 단순한 “쿼리 개수 증가”가 아니라, 연관 로딩 시점이 루트 결과 개수에 선형으로 종속되는 구조로 정의하는 것이 맞다.

### 3.2 N+1은 엔티티 수가 늘수록 비용이 같이 커지는 구조다

Hibernate 예시에서도 부모 여러 건을 먼저 조회한 뒤 각 부모의 자식 컬렉션을 개별 secondary select로 초기화하면, 부모 수가 늘수록 추가 SQL도 같이 늘어난다고 설명한다. 반대로 @BatchSize나 SUBSELECT는 이 반복을 줄여 SQL 수를 완화한다. 즉 N+1의 본질은 “한 번의 루트 조회 뒤에 같은 형태의 연관 조회가 반복적으로 누적되는 것”이다.

## 4. 기본 원칙

### 4.1 N+1의 기본 해법은 EAGER가 아니라 fetch plan 재설계다

Jakarta Persistence는 EAGER를 provider가 반드시 즉시 가져와야 하는 요구로 정의하고, LAZY는 힌트라고 설명한다. 하지만 Hibernate는 EAGER fetching is almost always a bad choice라고 명시하고, EAGER association을 JPQL query에서 JOIN FETCH로 포함하지 않으면 secondary select가 association마다 발생하여 N+1로 이어질 수 있다고 설명한다. 따라서 프로젝트 기본 원칙은 “EAGER로 막는다”가 아니라 “기본은 LAZY, 필요한 조회에서만 명시적으로 fetch plan을 지정한다”이다.

### 4.2 N+1 대응은 매핑이 아니라 use case 단위로 결정한다

Hibernate는 static fetching은 매핑에 정의되지만, dynamic fetching은 use-case centric하다고 설명하며, HQL/JPQL, entity graph, fetch profile 등으로 runtime fetch plan을 정할 수 있다고 안내한다. 즉 같은 엔티티라도 상세 조회, 목록 조회, 관리자 화면, 배치 처리에서 필요한 연관이 다르면 같은 fetch 전략을 강제하면 안 된다.

### 4.3 실제 판단 기준은 “엔티티 그래프”가 아니라 “실행된 SQL 개수와 shape”다

Hibernate는 프레임워크가 SQL을 대신 생성할수록, 실제로 생성된 SQL이 의도한 것과 같은지 반드시 확인해야 한다고 설명한다. 또한 DataSource proxy 방식 등을 이용하면 테스트 시 실행된 statement 수를 검증해 N+1 문제를 자동 탐지할 수 있다고 안내한다. 프로젝트 기준도 동일하다. N+1 여부는 추상적인 매핑만 보고 판단하지 않고, SQL 로그 또는 statement count 검증으로 확인한다.

## 5. 탐지 기준

### 5.1 N+1 탐지는 SQL 로그 확인을 기본으로 한다

Hibernate는 SQL logging을 통해 생성된 statement를 확인해야 한다고 설명한다. 프로젝트에서는 목록/상세/배치 주요 조회 경로에 대해, 루트 query 뒤에 같은 패턴의 secondary select가 엔티티 수만큼 반복되는지 확인하는 것을 기본 탐지 방식으로 둔다.

### 5.2 주요 조회 경로는 통합 테스트에서 statement count를 검증한다

Hibernate는 DataSource proxy 기반 접근을 쓰면 테스트 시 statement 수를 단언하여 N+1을 자동 탐지할 수 있다고 설명한다. 특정 라이브러리 선택은 이 문서 범위 밖이지만, 프로젝트 권장안은 핵심 repository/service 통합 테스트에 statement count 검증을 넣는 것이다. 단순 기능 통과만으로는 N+1 regressions를 막기 어렵다.

### 5.3 페이징 목록은 특히 별도 검증한다

Hibernate는 컬렉션 fetch join과 pagination을 함께 쓰면 limit가 DB가 아니라 메모리에서 적용될 수 있고, 성능 특성이 매우 나빠질 수 있다고 설명한다. 따라서 목록 API는 “N+1이 없는가”만 보지 말고, “컬렉션 fetch join으로 더 큰 문제를 만들지 않았는가”까지 같이 검증해야 한다.

## 6. 해결 우선순위

### 6.1 1차 선택: read-only 조회는 DTO projection을 우선 검토한다

Hibernate는 @BatchSize가 N+1보다 낫지만, 대부분의 경우 DTO projection이나 JOIN FETCH가 더 좋은 대안이라고 설명한다. 특히 목록 화면, 조회 전용 API, 백오피스 표 조회처럼 수정 목적이 아닌 use case에서는 엔티티 그래프를 억지로 채우기보다 필요한 컬럼만 조회하는 DTO projection이 가장 단순하고 신뢰도가 높다. 프로젝트 기본 우선순위에서도 read-only query는 DTO projection을 먼저 검토한다.

### 6.2 2차 선택: to-one 연관은 join fetch를 우선 검토한다

Hibernate는 acceptable performance를 위해 join fetch를 자주 사용해야 하며, LazyInitializationException을 피하는 가장 좋은 방법은 필요한 연관을 persistence context가 닫히기 전에 미리 fetch하는 것이라고 설명한다. 특히 JOIN FETCH는 @ManyToOne, @OneToOne, 그리고 많아야 하나의 컬렉션에 적합하다고 안내한다. 따라서 상세 조회나 소수 건 조회에서 필요한 to-one 연관은 join fetch가 기본 해법이다.

### 6.3 3차 선택: query 단위 fetch plan에는 Entity Graph를 사용한다

Jakarta Persistence는 fetchgraph와 loadgraph를 표준으로 정의하고, entity graph를 query나 find()의 fetch plan template로 사용할 수 있다고 설명한다. Spring Data JPA도 repository method에서 @EntityGraph로 named graph 또는 attributePaths() 기반 동적 graph를 지원한다. 프로젝트에서는 query method별로 필요한 연관이 비교적 명확하지만 JPQL fetch join을 직접 쓰고 싶지 않은 경우, Entity Graph를 공식 수단으로 사용한다.

### 6.4 4차 선택: batch fetching은 보조 완화 수단으로만 사용한다

Hibernate는 @BatchSize가 여러 uninitialized proxy/collection을 한 번에 가져와 SQL round trip 수를 줄인다고 설명한다. 또한 @BatchSize가 N+1보다 낫지만, 대부분은 DTO projection이나 JOIN FETCH가 더 좋은 대안이라고 명시한다. 따라서 프로젝트 규칙은, 구조적으로 secondary select를 유지해야 하는 경우에만 @BatchSize를 보조 수단으로 사용하고, 이것을 1차 해법으로 삼지 않는 것이다.

### 6.5 5차 선택: 같은 persistence context 안의 여러 컬렉션 초기화에는 SUBSELECT를 제한적으로 검토한다

Hibernate는 FetchMode.SUBSELECT가 한 번의 secondary select로 이전에 조회된 여러 owner의 같은 컬렉션 역할(role)을 함께 초기화하여 N+1을 피할 수 있다고 설명한다. 다만 이는 컬렉션에만 적용되는 Hibernate 전용 방식이며, query 구조를 단순화하는 1차 선택지는 아니다. 프로젝트에서는 동일한 root set를 먼저 가져온 뒤, 같은 컬렉션을 묶어서 지연 초기화해야 하는 특수 상황에서만 제한적으로 검토한다.

## 7. 페이징과 N+1 기준

### 7.1 페이징 query에 컬렉션 fetch join을 기본 금지한다

Hibernate는 fetch join이 paged query나 setFirstResult() / setMaxResults() 같은 제한 query에서는 보통 피해야 한다고 설명한다. 또한 컬렉션 또는 many-valued association에 fetch join을 적용한 상태에서 pagination을 쓰면 limit가 DB가 아니라 메모리에서 적용될 수 있고, 성능 특성이 매우 나쁘다고 설명한다. 따라서 프로젝트에서는 페이징 목록 + 컬렉션 fetch join을 기본 금지하고, 페이지 ID 조회 후 2차 query로 필요한 연관을 가져오는 분리 전략을 권장한다.

### 7.2 페이징 목록은 “ID 페이지 조회 + 후속 로딩” 구조를 기본 검토한다

Hibernate 공식 문서가 컬렉션 fetch join + pagination을 피하라고 명시하기 때문에, 실무 best practice는 먼저 root ID page를 안정적으로 조회하고, 그 결과 범위 안에서 필요한 to-one/collection/summary를 별도 query로 가져오는 구조다. 이 방식은 N+1을 피하면서도 page boundary를 안정적으로 유지한다. 이는 공식 제약 위에 얹는 프로젝트 권장안이다.

### 7.3 운영 환경에서는 hibernate.query.fail_on_pagination_over_collection_fetch를 검토한다

Hibernate는 hibernate.query.fail_on_pagination_over_collection_fetch 설정을 제공하며, 컬렉션 fetch join에 pagination이 걸려 limit가 메모리에서 적용되는 경우 예외를 던지게 할 수 있다고 설명한다. 기본값은 false라서 실수해도 조용히 넘어갈 수 있으므로, 프로젝트에서는 운영 안정성 관점에서 이 설정을 활성화할지 검토한다.

## 8. 트랜잭션 경계 기준

### 8.1 트랜잭션 밖 lazy 접근으로 N+1 또는 LazyInitializationException을 해결하려 하지 않는다

Hibernate는 LazyInitializationException의 가장 좋은 해결책은 persistence context가 닫히기 전에 필요한 연관을 미리 fetch하는 것이라고 설명한다. 따라서 controller, serializer, view layer가 lazy association을 우연히 초기화해 주기를 기대하는 방식은 금지한다. 프로젝트 기준은 application/service 경계 안에서 필요한 데이터를 모두 준비한 뒤 DTO/response model로 변환하는 것이다.

## 9. 프로젝트 권장안

### 9.1 기본 fetch는 LAZY, 해결은 query 단위로 한다

Hibernate가 EAGER를 피하고 LAZY를 기본으로 하라고 권장하므로, 프로젝트 기본값은 연관관계를 LAZY로 두고 N+1은 query별 fetch plan으로 해결한다. 즉 문제를 엔티티 선언으로 묶어 두지 않는다.

### 9.2 목록 API는 엔티티 그래프보다 DTO projection 우선

N+1이 가장 자주 터지는 곳은 목록 API다. 목록은 대개 수정 목적이 아니고, 필요한 필드 집합도 제한적이다. Hibernate도 DTO projection을 더 좋은 대안으로 제시하므로, 프로젝트에서는 목록 API와 백오피스 표 조회를 DTO projection 우선 대상으로 본다.

### 9.3 상세 API는 to-one join fetch 우선, 컬렉션은 최대 1개만 신중히

Hibernate는 JOIN FETCH가 to-one에 좋고, 많아야 하나의 컬렉션에만 쓰는 편이 낫다고 설명한다. 프로젝트도 상세 조회 기준으로 여러 to-one fetch join은 허용하되, 컬렉션 fetch join은 최대 1개까지만 신중히 허용한다.

### 9.4 여러 컬렉션이 동시에 필요하면 query를 분리한다

Hibernate는 여러 컬렉션 또는 to-many를 병렬 fetch join하면 Cartesian product가 발생하고 매우 나쁜 성능을 낼 수 있다고 명시한다. 따라서 프로젝트에서는 여러 컬렉션이 필요하면 “한 번에 다 fetch join”하지 않고, root query + 후속 batch/subselect/별도 query 조합으로 분리한다.

## 10. 금지 규칙

다음은 기본 금지다.

- N+1 해법으로 연관관계에 EAGER를 선언하는 것
- JPQL query에서 EAGER association을 빠뜨리고 secondary select에 의존하는 것
- 목록/페이징 query에서 컬렉션 fetch join을 사용하는 것
- 여러 컬렉션을 한 query에서 병렬 fetch join하는 것
- @BatchSize를 구조적 query 문제 은폐 수단으로 쓰는 것
- controller/serializer 단계에서 lazy association이 알아서 초기화되기를 기대하는 것
- SQL 로그나 statement count 검증 없이 “N+1이 없을 것”이라고 추정하는 것

이 금지 규칙은 Hibernate 공식 문서가 직접 경고하는 EAGER, parallel collection fetch join, paged collection fetch join, LazyInitializationException 대응 원칙을 바탕으로 한 best practice다.

## 11. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 query는 루트 1번 + 연관 N번 구조가 아닌가?
- 해결 방법으로 EAGER 선언 대신 query/use case 단위 fetch plan을 선택했는가?
- read-only 목록이라면 DTO projection을 먼저 검토했는가?
- to-one 연관은 join fetch 또는 entity graph로 해결했는가?
- 컬렉션 fetch join은 정말 1개 이내인가?
- pagination query와 컬렉션 fetch join 충돌을 검토했는가?
- @BatchSize는 보조 수단으로만 사용하고 있는가?
- 필요한 연관은 트랜잭션 안에서 모두 초기화되는가?
- SQL 로그 또는 statement count 테스트로 실제 query 수를 확인했는가?
- 운영 환경에서 fail_on_pagination_over_collection_fetch 검토 여부를 기록했는가?

위 체크리스트는 Hibernate의 fetching, join fetch, batch fetching, pagination 경고, LazyInitializationException guidance를 실무 운영 기준으로 압축한 것이다.
