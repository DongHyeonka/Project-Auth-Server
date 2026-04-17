# JPA Fetch Strategy 기준

## 1. 목적

이 문서는 JPA/Hibernate에서 연관관계 fetch 전략을 어떤 기준으로 선택할지 정의한다.

이 문서의 목표는 다음과 같다.

- 엔티티 매핑의 기본 fetch 정책을 안전하게 정한다
- 연관관계 로딩 시점을 엔티티 정의가 아니라 use case 기준으로 선택한다
- EAGER 기본값 남용, 무분별한 fetch join, 트랜잭션 밖 lazy 접근 같은 위험한 패턴을 줄인다
- 실제 실행 SQL 기준으로 fetch 전략을 판단하는 습관을 만든다

Jakarta Persistence는 FetchType을 연관 데이터 로딩 정책으로 정의하고, Hibernate는 fetching이 성능에 가장 큰 영향을 미치는 요소 중 하나라고 설명한다. 또한 Hibernate는 연관 데이터 과다 로딩이 대부분의 JPA 애플리케이션에서 가장 큰 성능 문제라고 지적한다.

## 2. 근거 수준

- Official: Jakarta Persistence / Hibernate / Spring Data JPA 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 Jakarta Persistence 3.2 spec의 연관관계 기본 fetch 규칙, fetch graph / load graph semantics, Hibernate User Guide의 fetch join / batch fetching / eager fetching 권장사항, Spring Data JPA의 @EntityGraph 지원 문서를 기반으로 작성한다.

## 3. 기본 원칙

### 3.1 fetch 전략은 “엔티티 구조”가 아니라 “use case의 읽기 경계”를 표현해야 한다

JPA의 fetch는 연관 속성을 언제 어떤 범위까지 가져올지를 결정하는 정책이다. 따라서 fetch 전략은 엔티티를 선언할 때 한 번 정해 두고 끝나는 설정이 아니라, 어떤 조회에서 어떤 연관 데이터가 필요한지를 기준으로 해석해야 한다. Hibernate도 정적 매핑 기본값은 보수적으로 두고, eager 요구는 동적으로 적용하는 방향을 권장한다.

### 3.2 fetch 전략은 cascade, orphanRemoval, 연관관계 소유자 개념과 다르다

fetch는 로딩 시점 정책이고, cascade와 orphanRemoval은 생명주기 전파 정책이다. 같은 연관관계라도 쓰기 전파와 읽기 로딩 정책은 별개로 설계해야 한다. Jakarta Persistence는 연관관계 애노테이션에서 fetch와 cascade를 독립 요소로 정의한다.

### 3.3 fetch 전략 판단 기준은 “엔티티 그래프”가 아니라 “실제 SQL 개수와 shape”다

Hibernate는 join fetch, batch fetching, entity graph, secondary select 같은 여러 전략을 제공하지만, 실제 성능은 어떤 SQL이 몇 번 나가느냐에 의해 결정된다. 따라서 fetch 전략은 추상 개념이 아니라 실제 SQL과 실행 계획으로 검증해야 한다.

## 4. 기본 fetch type 기준

### 4.1 JPA 기본값은 to-one EAGER, to-many LAZY다

Jakarta Persistence 3.2 spec에서 @ManyToOne과 @OneToOne의 기본 fetch는 EAGER, @OneToMany와 @ManyToMany의 기본 fetch는 LAZY다. 또한 spec은 EAGER는 provider가 반드시 eager하게 가져와야 하는 요구이고, LAZY는 지연 로딩에 대한 힌트라고 설명한다.

### 4.2 프로젝트 기본값은 “모든 연관관계 LAZY 명시”다

JPA 기본값이 존재하더라도, 프로젝트 기본 규칙은 연관관계에 fetch를 명시하고 기본적으로 모두 LAZY로 둔다이다. Hibernate는 EAGER fetching is almost always a bad choice라고 설명하고, 모든 연관관계를 LAZY로 두고 eager 요구는 동적으로 적용하는 편이 낫다고 권장한다. 또한 EAGER는 query별로 덮어쓸 수 없고, JPQL query에서 빠뜨리면 secondary select가 발생해 N+1 문제를 만들 수 있다고 설명한다.

프로젝트 규칙:

다음을 기본으로 한다.

- @ManyToOne(fetch = FetchType.LAZY)
- @OneToOne(fetch = FetchType.LAZY)
- @OneToMany(fetch = FetchType.LAZY) 또는 기본값 유지
- @ManyToMany(fetch = FetchType.LAZY) 또는 기본값 유지

### 4.3 LAZY는 “보장”이 아니라 힌트라는 점을 전제로 설계한다

Jakarta Persistence spec은 LAZY를 힌트로 정의하고, 구현체가 필요하면 eager하게 가져오는 것을 허용한다. 따라서 “반드시 SQL이 늦게 나간다”는 수준의 가정 위에 설계하면 안 된다. 프로젝트 기준은 LAZY를 기본값으로 두되, 필요한 데이터는 query 또는 entity graph에서 명시적으로 가져오는 방식이다.

## 5. EAGER 기준

### 5.1 연관관계 EAGER는 기본 금지다

Hibernate는 EAGER가 거의 항상 나쁜 선택이며, query 단위로 덮어쓸 수 없고, 필요하지 않은 연관관계까지 항상 가져오게 만든다고 설명한다. 특히 JPQL query에서 EAGER 연관을 fetch join으로 포함하지 않으면 secondary select가 발생할 수 있고, 이것이 N+1 문제로 이어질 수 있다.

프로젝트 규칙:

- 연관관계에 EAGER를 기본 금지
- “항상 필요해 보인다”는 직관만으로 EAGER를 사용하지 않는다
- EAGER가 필요해 보여도 먼저 use case 단위 fetch join / entity graph / DTO projection을 검토한다

### 5.2 EAGER는 “항상 같이 로딩돼야 하는 구조”가 아니라 “항상 비용을 강제하는 설정”이다

to-one EAGER는 편해 보이지만, 실제로는 조회 경로 대부분에서 필요 없는 조인 또는 secondary select 비용을 고정시킨다. Hibernate는 entity query에서 EAGER association fetch policy는 query 단위로 override되지 않으므로 secondary select가 필요해질 수 있다고 설명한다.

프로젝트 규칙:

- 기본 엔티티 설계 단계에서 “읽기 편의” 때문에 EAGER를 채택하지 않는다
- domain model convenience보다 SQL 비용의 고정화를 더 크게 본다

## 6. 동적 fetch 전략 기준

### 6.1 기본값은 LAZY, eager 요구는 query/use case 단위로 적용한다

Hibernate는 정적으로는 모든 association을 lazy로 두고, eager 요구는 dynamic fetching strategy로 적용하는 것을 권장한다. 이 원칙은 use case마다 필요한 데이터 폭이 다르다는 현실과 잘 맞는다.

프로젝트 규칙:

- 엔티티 매핑은 보수적
- 특정 조회 화면/상세 조회/배치 job에서만 eager 요구를 명시
- 같은 엔티티라도 query별로 fetch plan이 달라질 수 있음을 전제로 한다

### 6.2 to-one 중심 상세 조회는 join fetch를 우선 검토한다

Hibernate는 join fetch가 laziness를 override하여 같은 SQL join으로 연관 데이터를 가져오는 방식이라고 설명하고, acceptable performance를 위해 자주 사용하게 된다고 안내한다. 또한 Hibernate는 JOIN FETCH가 @ManyToOne / @OneToOne에 특히 적합하다고 설명한다.

프로젝트 규칙:

- 상세 조회에서 필요한 to-one 연관은 join fetch를 우선 검토
- 한 query 안에서 여러 to-one fetch join은 허용 범위로 본다
- 단, 실제 SQL row 수 증가를 설명할 수 있어야 한다

### 6.3 컬렉션 fetch는 “한 번에 몇 개를 같이 가져올 것인가”를 더 엄격하게 본다

Hibernate는 여러 to-one을 함께 fetch join하는 것은 안전하지만, 여러 to-many/collection을 병렬 fetch join하면 Cartesian product가 발생해 매우 나쁜 성능을 낼 수 있다고 설명한다. 또한 fetch join은 보통 제한/페이징 query에서 피해야 한다고 명시한다.

프로젝트 규칙:

- 한 query에서 여러 컬렉션 fetch join은 기본 금지
- 컬렉션 fetch join은 최대 1개까지만 매우 신중하게 허용
- 목록/페이징 query에서 컬렉션 fetch join은 기본 금지
- 여러 컬렉션이 필요하면 secondary query, batch fetching, DTO 조회 분리 등을 검토한다

### 6.4 read-only use case는 DTO projection을 우선 검토한다

Hibernate는 read-only transaction에서는 DTO projection이 더 적절하며, 필요한 컬럼만 선택할 수 있고 persistence context 부담도 줄인다고 설명한다. fetch 전략 논의가 항상 엔티티 조회여야 하는 것은 아니다.

프로젝트 규칙:

- 수정이 목적이 아닌 목록/조회 전용 use case는 DTO projection 우선 검토
- 엔티티 그래프 전체를 굳이 관리할 필요가 없는 화면 조회는 엔티티보다 projection을 선호
- “조회라서 일단 entity”를 기본값으로 두지 않는다

## 7. Entity Graph 기준

### 7.1 fetch plan 제어가 필요하면 Entity Graph를 공식 수단으로 사용한다

Jakarta Persistence는 entity graph를 query나 find() operation에 적용할 수 있는 fetch plan template로 정의한다. fetchgraph는 명시한 속성만 eager로 취급하고 나머지는 LAZY로 취급하며, loadgraph는 명시한 속성은 eager로 취급하되 나머지는 원래의 default/spec fetch를 따른다.

프로젝트 규칙:

- query별 fetch 계획이 분명하면 entity graph 사용 허용
- “기본 연관관계는 LAZY, 특정 조회에서만 일부 연관을 함께 로딩” 패턴에 적합
- fetchgraph와 loadgraph 의미 차이를 구분해서 사용한다

### 7.2 Spring Data JPA에서는 @EntityGraph를 use case 단위로 사용한다

Spring Data JPA는 repository method에 @EntityGraph를 붙여 JPA 2.1 EntityGraph를 설정할 수 있고, attributePaths()를 이용한 동적 fetch-graph도 지원한다. 이는 정적 매핑을 건드리지 않고 repository method 단위로 fetch plan을 선언하기에 적합하다.

프로젝트 규칙:

- repository 메서드 단위 eager 요구에는 @EntityGraph 허용
- attributePaths는 필요한 경로만 최소 범위로 선언
- 재사용성이 높으면 named entity graph 검토

## 8. Batch Fetching 기준

### 8.1 batch fetching은 LAZY secondary select를 완화하는 보조 수단이다

Hibernate는 @BatchSize가 여러 uninitialized proxy 또는 collection을 한 번에 가져오게 해 주는 최적화라고 설명한다. 이는 LAZY select fetching을 완전히 없애는 것이 아니라 round trip 수를 줄이는 방식이다.

프로젝트 규칙:

- 여러 엔티티의 같은 LAZY association을 뒤이어 접근하는 패턴이면 @BatchSize 검토
- 목록 이후 자식 컬렉션/연관을 묶어서 초기화하는 경우에 적합
- 하지만 기본 전략은 아니고, join fetch/DTO projection보다 한 단계 뒤의 최적화 수단으로 본다

### 8.2 batch fetching은 응급처치이지 기본 해법이 아니다

Hibernate는 @BatchSize가 N+1보다 낫지만, 대부분의 경우 DTO projection 또는 JOIN FETCH가 더 좋은 대안이라고 설명한다. 따라서 batch fetching은 “이미 LAZY secondary select 구조를 유지해야 하는 상황”에서 보조적으로 검토하는 것이 맞다.

프로젝트 규칙:

- 먼저 query 구조 자체를 바꿀 수 있는지 검토
- 그래도 여러 연관 초기화가 남으면 @BatchSize를 보조적으로 사용
- @BatchSize를 남발해서 구조적 query 문제를 숨기지 않는다

## 9. 트랜잭션 경계와 LazyInitialization 기준

### 9.1 LAZY 연관은 persistence context가 열려 있는 동안 필요한 범위까지 초기화해야 한다

Hibernate는 필요한 연관은 persistence context가 닫히기 전에 가져와야 하며, 그렇지 않으면 LazyInitializationException이 발생한다고 설명한다. 또한 가장 좋은 해결책은 필요한 association을 미리 fetch하는 것이라고 안내한다.

프로젝트 규칙:

- 트랜잭션 밖에서 엔티티 lazy 연관 접근을 기대하지 않는다
- controller/view 단계에서 우연히 lazy loading이 되기를 기대하는 패턴을 금지
- application service 내부에서 필요한 fetch plan을 끝낸 뒤 DTO/응답 모델로 변환한다

### 9.2 fetch 전략 문제를 presentation 계층으로 밀어내지 않는다

지연 로딩 오류를 화면/직렬화 단계에서 우회하는 것은 fetch 전략 설계 실패를 뒤로 미루는 것이다. 프로젝트 기준은 조회 use case 내부에서 필요한 데이터를 명시적으로 준비하고, presentation 경계 밖으로 미완성 entity graph를 흘리지 않는 것이다. Hibernate의 권장도 필요한 association을 persistence context 종료 전에 fetch하는 방향이다.

## 10. 문서 경계

이 문서는 매핑 기본값과 fetch plan 선택 기준을 다룬다.
다음 내용은 별도 문서에서 확장한다.

- N+1 탐지와 방지 패턴
- pagination query와 fetch join 충돌
- query 최적화와 index 설계
- DTO projection 기준
- OSIV/open-in-view 운영 정책

## 11. 금지 규칙

다음은 기본 금지다.

- @ManyToOne, @OneToOne 기본값을 그대로 두고 암묵적 EAGER에 의존
- 연관관계에 EAGER를 편의상 선언
- 여러 컬렉션을 한 query에서 동시에 fetch join
- 페이징 query에 컬렉션 fetch join 사용
- @BatchSize를 구조적 query 문제 은폐 수단으로 사용
- 트랜잭션 밖 lazy initialization 기대
- controller/serializer가 entity lazy loading을 유발하는 구조
- 조회 전용 use case인데도 무조건 entity graph 전체를 로딩

이 금지 규칙은 Jakarta Persistence 기본 동작과 Hibernate 공식 권장사항 위에 얹는 실무 best practice다. 특히 EAGER, multiple collection fetch join, transaction boundary 밖 lazy access는 신뢰도 낮은 설계를 만드는 대표 패턴이다.

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 모든 연관관계의 fetch type이 명시적이거나, 최소한 암묵 기본값을 알고 있는가?
- to-one 연관에 기본 EAGER를 그대로 두지 않았는가?
- 이 조회는 entity가 정말 필요한가, DTO projection이 더 맞는가?
- eager 요구를 매핑이 아니라 query/use case 단위로 풀고 있는가?
- fetch join이 to-one 중심인지, 컬렉션은 최대 1개 이내인지 확인했는가?
- pagination query와 fetch join 충돌을 검토했는가?
- @EntityGraph 또는 fetchgraph/loadgraph 의미를 올바르게 선택했는가?
- @BatchSize가 1차 해법이 아니라 보조 최적화인지 설명할 수 있는가?
- 필요한 lazy 연관을 트랜잭션 안에서 모두 준비했는가?
- 실제 SQL 개수와 shape를 로그/테스트로 검증했는가?
