# Index 기준

## 1. 목적

이 문서는 PostgreSQL 인덱스를 어떤 기준으로 생성하고, 어떤 경우에 멀티 컬럼 / partial / expression / covering index를 사용할지 정의한다.

이 문서의 목표는 다음과 같다.

- 인덱스를 “많을수록 좋다”가 아니라 읽기 이익과 쓰기 비용의 교환으로 이해한다
- 조회 패턴에 맞는 인덱스를 만들고, 습관적 인덱스 추가를 줄인다
- B-tree를 기본값으로 두되, GIN / BRIN 같은 특수 인덱스는 근거가 있을 때만 사용한다
- JPA/Hibernate를 쓰더라도 인덱스 설계 기준은 엔티티 구조가 아니라 실제 SQL 접근 패턴을 기준으로 잡는다

PostgreSQL 공식 문서도 인덱스는 조회를 빠르게 만들 수 있지만, 동시에 INSERT/UPDATE/DELETE 비용과 저장 공간 비용을 추가하므로 신중하게 사용해야 한다고 설명한다.

## 2. 근거 수준

- Official: PostgreSQL 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL 공식 문서의 Chapter 11(Indexes), CREATE INDEX, EXPLAIN, 통계 뷰 문서를 기준으로 작성한다. 인덱스 종류, 멀티 컬럼 규칙, partial / expression / covering index, 운영 중 생성 방식은 모두 공식 문서로 직접 확인 가능하다.

## 3. 기본 원칙

### 3.1 인덱스는 컬럼 기준이 아니라 쿼리 기준으로 만든다

인덱스는 “이 컬럼이 중요해 보이니까”가 아니라 어떤 WHERE / JOIN / ORDER BY / pagination 경로를 빠르게 만들고 싶은가를 기준으로 설계해야 한다. PostgreSQL planner는 쿼리 구조와 통계에 따라 인덱스 사용 여부를 결정하므로, 인덱스 정의는 실제 질의 패턴과 분리해서 생각할 수 없다. EXPLAIN은 planner가 선택한 실행 계획을 보여 주며, EXPLAIN ANALYZE는 실제 실행 통계까지 확인하게 해 준다.

프로젝트 규칙:

- 인덱스 생성 이유를 항상 쿼리 패턴으로 설명할 수 있어야 한다
- “자주 조회될 것 같다” 수준의 추측만으로 인덱스를 추가하지 않는다
- 인덱스 추가/변경 전후는 EXPLAIN (ANALYZE)로 확인하는 것을 기본으로 한다

### 3.2 모든 인덱스는 쓰기 비용과 저장 비용을 만든다

PostgreSQL 공식 문서는 인덱스가 테이블과 별도로 저장되는 secondary index이며, 생성 후에는 테이블 변경 시점마다 계속 동기화되어야 한다고 설명한다. 또한 거의 사용되지 않는 인덱스는 제거하는 편이 좋다고 안내한다.

프로젝트 규칙:

- 인덱스 하나를 추가할 때마다 INSERT/UPDATE/DELETE 비용이 증가한다고 가정한다
- 사용 빈도가 낮거나 중복되는 인덱스는 유지하지 않는다
- “읽기 성능 개선”만 보고 추가하고, 쓰기 비용은 무시하는 설계를 금지한다

### 3.3 제약이 이미 만든 인덱스를 중복 생성하지 않는다

PostgreSQL은 PRIMARY KEY와 UNIQUE 제약을 만들면 자동으로 unique B-tree 인덱스를 생성한다. 따라서 PK/UNIQUE 컬럼에 동일한 의미의 인덱스를 다시 만드는 것은 중복인 경우가 많다.

프로젝트 규칙:

- PK/UNIQUE 제약이 이미 만든 인덱스를 먼저 확인한다
- 같은 컬럼, 같은 순서, 같은 의미의 중복 인덱스를 금지한다
- 제약 인덱스로 해결되지 않는 조회 패턴이 있을 때만 별도 인덱스를 검토한다

## 4. 기본 인덱스 타입 기준

### 4.1 기본값은 B-tree

PostgreSQL은 여러 인덱스 타입을 제공하지만, CREATE INDEX의 기본은 B-tree이며 가장 일반적인 상황에 적합하다고 설명한다. B-tree는 정렬 가능한 값에 대해 equality, range, BETWEEN, IN, IS NULL, prefix LIKE 'foo%' 같은 조건과 ORDER BY 처리에 특히 강하다.

프로젝트 규칙:

- 특별한 이유가 없으면 기본 인덱스 타입은 B-tree
- equality 조회, 범위 조회, 정렬, 일반 pagination은 먼저 B-tree로 검토
- 비-B-tree는 연산자/데이터 타입 요구가 분명할 때만 사용

### 4.2 비-B-tree는 데이터 특성과 연산자가 맞을 때만 사용한다

PostgreSQL은 GIN, GiST, BRIN 등 각 인덱스 타입이 서로 다른 알고리즘과 적합한 연산자 집합을 가진다고 설명한다. 예를 들어 GIN은 composite value 안의 element 검색에 적합하고, BRIN은 물리적 저장 순서와 자연 상관성이 있는 아주 큰 테이블에 적합하다. jsonb 검색도 GIN이 대표적이다.

프로젝트 규칙:

- jsonb containment / key search는 GIN을 우선 검토
- append-only에 가깝고 시간/순번 컬럼이 물리 순서와 잘 맞는 대형 테이블은 BRIN을 검토
- Hash/GiST/SP-GiST는 특별한 연산 요구가 분명할 때만 사용
- “성능이 안 나와서 일단 GIN/BRIN” 같은 추측성 선택을 금지한다

## 5. 단일 컬럼 인덱스 기준

### 5.1 먼저 단일 컬럼 인덱스로 충분한지 본다

PostgreSQL 공식 문서는 멀티 컬럼 인덱스가 가능하지만, 대부분의 상황에서는 단일 컬럼 인덱스가 충분하며 공간과 유지 비용 면에서 유리하다고 설명한다. 멀티 컬럼 인덱스는 신중히 사용해야 하고, 3개를 넘는 키 컬럼은 매우 정형화된 사용 패턴이 아니면 도움이 되기 어렵다고 안내한다.

프로젝트 규칙:

- 인덱스 검토의 출발점은 단일 컬럼 인덱스
- 멀티 컬럼 인덱스는 실제 복합 검색 패턴이 반복될 때만 추가
- “혹시 도움이 될 수 있으니 여러 컬럼을 한 번에 묶는” 설계를 금지한다

### 5.2 FK 컬럼은 조회/삭제 경로를 보고 단일 인덱스를 검토한다

PostgreSQL은 FK를 선언해도 참조하는 쪽 컬럼에 인덱스를 자동 생성하지 않는다. 하지만 부모 삭제/갱신과 자식 조인 경로에서 FK 컬럼 인덱스가 자주 필요하다.

프로젝트 규칙:

- FK 컬럼은 기본적으로 인덱스 후보로 본다
- 단, 항상 자동 생성하지는 않고 실제 join / delete / lookup 경로를 함께 본다
- FK가 있다고 해서 인덱스가 이미 있다고 가정하지 않는다

## 6. 멀티 컬럼 인덱스 기준

### 6.1 B-tree 멀티 컬럼 인덱스는 왼쪽 컬럼 순서가 핵심이다

PostgreSQL 공식 문서는 B-tree 멀티 컬럼 인덱스가 leading(leftmost) columns 제약에 가장 효율적이라고 설명한다. 선행 컬럼에 equality 조건이 있고, 그 다음 첫 non-equality 컬럼에 range 조건이 있을 때 인덱스가 가장 잘 작동한다. 뒤쪽 컬럼 조건은 table 방문을 줄이는 데는 도움을 줄 수 있어도, 인덱스 스캔 범위를 줄이는 효과는 제한적일 수 있다.

프로젝트 규칙:

- 멀티 컬럼 B-tree는 가장 자주 쓰는 equality 필터를 왼쪽에 둔다
- 그 다음에 range / 정렬 컬럼을 배치한다
- 단순히 엔티티 필드 순서대로 인덱스 순서를 정하지 않는다

### 6.2 멀티 컬럼 인덱스는 “자주 함께 쓰는 조건”에만 사용한다

PostgreSQL은 separate index들을 bitmap scan으로 조합할 수 있다. 즉 (x, y) 멀티 컬럼 인덱스만이 유일한 선택지는 아니며, 경우에 따라서는 x 인덱스와 y 인덱스를 따로 두고 planner가 결합하는 편이 나을 수 있다.

프로젝트 규칙:

- 항상 함께 쓰이는 조건 조합이면 멀티 컬럼 인덱스를 우선 검토
- 독립적으로도 자주 쓰이는 컬럼이면 separate indexes 가능성도 같이 본다
- 멀티 컬럼 인덱스가 있으면 단일 인덱스가 전부 불필요하다고 단정하지 않는다

### 6.3 4개 이상 키 컬럼 인덱스는 예외적으로만 허용한다

공식 문서도 멀티 컬럼 인덱스는 신중히 써야 하며, 3개를 넘는 경우는 사용 패턴이 매우 정형화되지 않으면 대개 도움이 되기 어렵다고 설명한다.

프로젝트 규칙:

- 키 컬럼 4개 이상 인덱스는 기본 금지
- 정말 필요한 경우에도 EXPLAIN (ANALYZE) 근거와 함께 예외적으로 허용
- “모든 검색 조건을 한 인덱스로 커버”하려는 설계를 금지한다

## 7. ORDER BY / 정렬 기준

### 7.1 정렬 최적화 기본값은 B-tree

PostgreSQL 공식 문서는 현재 정렬된 출력(ORDER BY)을 직접 제공할 수 있는 인덱스는 B-tree뿐이라고 설명한다. 또한 소수 행을 가져오는 쿼리에서는 인덱스로 정렬을 피하는 이점이 크지만, 테이블 대부분을 읽는 경우에는 sequential access + explicit sort가 더 빠를 수 있다고 안내한다.

프로젝트 규칙:

- ORDER BY 최적화는 B-tree 기준으로 설계
- 작은 결과 집합 + 정렬 회피가 중요한 쿼리에 인덱스 정렬 최적화를 적용
- 대량 스캔 쿼리에 무조건 정렬용 인덱스를 추가하지 않는다

### 7.2 단일 컬럼 DESC 인덱스는 보통 불필요하다

PostgreSQL은 B-tree를 forward/backward 모두 스캔할 수 있으므로, 단일 컬럼 DESC 전용 인덱스는 일반적으로 별 이점이 없다. 특별한 의미가 생기는 것은 ORDER BY x ASC, y DESC처럼 혼합 정렬 방향의 멀티 컬럼 인덱스일 때다.

프로젝트 규칙:

- 단일 컬럼 DESC 인덱스는 기본 금지
- mixed ordering 쿼리가 빈번할 때만 (a ASC, b DESC) 같은 특수 정렬 인덱스를 검토
- 정렬 방향 지정은 실제 쿼리 계약이 있을 때만 사용한다

## 8. Partial Index 기준

### 8.1 일부 행만 자주 조회될 때 partial index를 검토한다

PostgreSQL은 WHERE predicate가 붙은 partial index를 지원하며, 전체 행이 아니라 “더 유용한 일부 행”만 인덱싱할 수 있다고 설명한다. 이는 soft delete, 미처리 상태, 활성 행 등 특정 부분집합만 자주 조회될 때 유효하다.

프로젝트 규칙:

다음 같은 패턴에 partial index를 검토한다.

- soft delete에서 deleted_at IS NULL
- 활성 행만 자주 조회하는 경우 is_active = true
- 처리 대기 상태만 자주 조회하는 경우 processed_at IS NULL

### 8.2 partial index는 predicate가 쿼리와 잘 맞아야 한다

PostgreSQL 공식 문서는 partial index가 사용되려면 planner가 쿼리의 WHERE 조건이 인덱스 predicate를 함의한다고 인식해야 하며, 일반적인 theorem prover는 없다고 설명한다. 단순 부등식은 일부 인식하지만, 대개는 쿼리 조건이 partial index predicate와 매우 가깝게 맞아야 한다. 또한 matching은 planning time에 일어나므로, parameterized query clause는 partial index와 잘 맞지 않는다.

프로젝트 규칙:

- partial index predicate는 쿼리 조건과 동일하거나 매우 가깝게 유지
- predicate를 과하게 복잡하게 만들지 않는다
- parameterized dynamic predicate에 의존하는 partial index 설계를 지양한다

### 8.3 partial index는 분포가 안정적일 때 더 적합하다

PostgreSQL은 partial index가 common values를 제외하는 방식일 때, 데이터 분포가 자주 바뀌지 않는 경우에 더 적합하다고 설명한다. 분포가 변하면 재생성/재조정 비용이 생긴다.

프로젝트 규칙:

- partial index는 “오랫동안 hot subset이 유지되는 조건”에 사용
- rapidly changing predicate에는 기본 해법으로 사용하지 않는다
- 분포가 자주 바뀌는 경우 일반 인덱스 또는 쿼리 재설계를 우선 검토한다

## 9. Expression Index 기준

### 9.1 컬럼이 아니라 표현식으로 검색한다면 expression index를 검토한다

PostgreSQL은 expression index를 공식 지원하며, lower(col) 같은 계산 결과를 인덱싱할 수 있다. 대소문자 무시 검색처럼 쿼리가 항상 동일한 표현식을 적용할 때 유효하다.

프로젝트 규칙:

다음에 한해 expression index를 검토한다.

- lower(email) 기반의 case-insensitive search
- 특정 JSON 경로/표현식 기반 검색
- 문자열 결합 또는 계산 결과를 자주 찾는 경우

### 9.2 함수/표현식은 immutable이어야 한다

PostgreSQL은 인덱스 정의에 사용되는 함수와 연산자는 immutable이어야 한다고 설명한다. 결과가 외부 상태나 현재 시간 등에 따라 바뀌면 인덱스 의미가 깨진다.

프로젝트 규칙:

- expression index에는 immutable 함수만 사용
- now() 같은 시간 의존 표현식은 금지
- 사용자 정의 함수는 volatility 속성을 확인한 뒤에만 사용

### 9.3 expression index는 쓰기 비용이 더 크다

PostgreSQL 공식 문서는 expression index가 삽입과 non-HOT update마다 표현식을 계산해야 하므로 유지 비용이 높다고 설명한다. 검색 성능 이점이 확실할 때만 쓰는 것이 안전하다.

프로젝트 규칙:

- expression index는 read-heavy 경로에서만 채택
- 단순 컬럼 인덱스로 충분하면 expression index를 만들지 않는다
- “쿼리를 고치기 어렵다”는 이유만으로 남발하지 않는다

## 10. Covering Index / INCLUDE 기준

### 10.1 INCLUDE는 index-only scan이 실제로 이득일 때만 사용한다

PostgreSQL은 INCLUDE로 non-key column을 인덱스 leaf tuple에 넣어 index-only scan을 돕는다. 하지만 index-only scan은 인덱스 타입 지원, 쿼리가 인덱스 내 컬럼만 참조할 것, 그리고 visibility map 상태가 좋아야 실제 이점이 크다. 특히 자주 변경되는 테이블에서는 heap 방문을 완전히 피하지 못할 수 있다.

프로젝트 규칙:

- 자주 실행되는 read-heavy 목록 조회에서만 INCLUDE를 검토
- 자주 갱신되는 hot table에는 신중히 적용
- 단순히 “커버링이 좋아 보인다”는 이유로 추가하지 않는다

### 10.2 INCLUDE 컬럼은 보수적으로 선택한다

PostgreSQL은 INCLUDE 컬럼이 인덱스 크기를 키우고, 너무 크면 삽입 실패까지 일어날 수 있으며, B-tree deduplication도 사용되지 않는다고 설명한다.

프로젝트 규칙:

- INCLUDE에는 작은 payload 컬럼만 넣는다
- 큰 text/json/blob 계열 컬럼 포함은 기본 금지
- key column과 non-key column을 구분해서 설계한다

## 11. 특수 인덱스 사용 기준

### 11.1 jsonb 검색은 기본적으로 GIN을 검토한다

PostgreSQL 공식 문서는 jsonb에서 key exists, containment, jsonpath matching을 효율적으로 처리하기 위해 GIN 인덱스를 사용할 수 있다고 설명한다. 또한 전체 jsonb 컬럼에 GIN을 두는 방식과, 자주 조회하는 하위 경로에 expression + GIN을 두는 방식의 trade-off도 설명한다.

프로젝트 규칙:

- jsonb 전체 containment 조회가 많으면 GIN
- 특정 하위 키/배열 경로만 자주 조회하면 expression index + GIN도 검토
- 단, 핵심 필터 조건이면 jsonb보다 일반 컬럼으로 승격하는 것을 우선 검토한다

### 11.2 대형 append-only 테이블은 BRIN을 검토한다

PostgreSQL은 BRIN이 물리적 저장 위치와 자연 상관성이 있는 매우 큰 테이블에 적합하며, 인덱스 크기가 매우 작고 큰 범위를 건너뛸 수 있다고 설명한다. 다만 lossy index라서 재검사가 필요하고, 요약 단위(pages_per_range)와 summarization 특성을 이해하고 써야 한다.

프로젝트 규칙:

- audit log, event log, append-only history 같은 대형 테이블에 검토
- created_at, 증가하는 sequence/id처럼 물리 순서와 상관성이 큰 컬럼에 우선 적용
- OLTP 소형 테이블에 BRIN을 기본값으로 사용하지 않는다

## 12. 운영 기준

### 12.1 운영 중 대형 테이블 인덱스 추가는 CONCURRENTLY를 기본 검토한다

PostgreSQL은 일반 CREATE INDEX가 읽기는 허용하지만 쓰기를 막고, 운영 환경에서는 이 잠금이 받아들이기 어려울 수 있다고 설명한다. CREATE INDEX CONCURRENTLY는 writes를 막지 않고 인덱스를 만들 수 있지만 여러 caveat가 있다.

프로젝트 규칙:

- 운영 중 대형 테이블 인덱스 추가는 CREATE INDEX CONCURRENTLY를 우선 검토
- 로컬/테스트/배치 전용 환경에서는 일반 CREATE INDEX도 가능
- 구체적 migration 절차는 migration 문서에서 확장한다

### 12.2 CONCURRENTLY는 만능이 아니다

PostgreSQL 공식 문서는 concurrent build가 실패하면 invalid index가 남을 수 있고, transaction block 안에서 실행할 수 없으며, 같은 테이블에서는 동시에 하나만 수행할 수 있다고 설명한다. partitioned table에도 제약이 있다.

프로젝트 규칙:

- CONCURRENTLY는 운영 안전성 도구이지 단순 기본값이 아니다
- 실패 시 invalid index 정리 절차를 준비한다
- migration 도구에서 transaction wrapping과 충돌하는지 먼저 확인한다

### 12.3 인덱스 효과 검증은 EXPLAIN + 통계 뷰로 확인한다

PostgreSQL은 EXPLAIN/EXPLAIN ANALYZE로 실행 계획을 확인할 수 있고, pg_stat_user_indexes/pg_stat_all_indexes로 index scan 수와 접근 통계를 확인할 수 있다고 설명한다. per-index statistics는 어떤 인덱스가 실제로 사용되는지 판단하는 데 유용하다.

프로젝트 규칙:

- 인덱스 추가 전후는 EXPLAIN (ANALYZE)로 확인
- 장기적으로는 pg_stat_user_indexes로 사용 빈도 확인
- 거의 쓰이지 않는 비제약 인덱스는 제거 후보로 관리

## 13. 네이밍 기준

프로젝트에서는 인덱스 이름을 명시적으로 선언한다. PostgreSQL은 이름을 생략하면 자동 생성 이름을 만들지만, 운영 추적성과 migration diff 명확성을 위해 명시 이름이 더 안전하다.

프로젝트 규칙:

- 일반 인덱스: ix_<table>__<columns>
- partial index: ix_<table>__<columns>__<predicate_hint>
- unique index: uq_<table>__<columns>
- expression index: ix_<table>__<expression_hint>

## 14. 금지 규칙

다음은 기본 금지다.

- 모든 FK/모든 컬럼에 기계적으로 인덱스를 생성
- PK/UNIQUE가 이미 만든 인덱스를 중복 생성
- 근거 없이 4개 이상 키 컬럼 멀티 컬럼 인덱스 생성
- partial index predicate를 지나치게 복잡하게 설계
- mutable 함수 기반 expression index 생성
- 큰 payload 컬럼을 INCLUDE에 무분별하게 추가
- 단일 컬럼 DESC 인덱스를 습관적으로 생성
- jsonb/배열/전문검색 요구가 아닌데도 GIN을 남발
- 대형 운영 테이블에 일반 CREATE INDEX를 무심코 실행

이 금지 규칙은 PostgreSQL 공식 문서의 동작 특성과 실무 운영 리스크를 함께 반영한 best practice다.

## 15. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 인덱스는 특정 쿼리 패턴으로 설명 가능한가?
- PK/UNIQUE/FK가 이미 제공하는 인덱스와 중복되지 않는가?
- 단일 컬럼 인덱스로 충분한지 먼저 검토했는가?
- 멀티 컬럼이면 왼쪽 컬럼 순서가 실제 필터 패턴과 맞는가?
- 정렬 최적화가 정말 필요한 쿼리인가?
- partial index predicate가 실제 쿼리와 정확히 맞는가?
- expression index의 함수/연산자가 immutable인가?
- INCLUDE 컬럼이 작고, index-only scan 이점이 실제로 기대되는가?
- 비-B-tree 선택 이유가 연산자/데이터 타입 특성으로 설명되는가?
- 운영 반영 시 CONCURRENTLY 필요 여부를 검토했는가?
- EXPLAIN (ANALYZE) 또는 통계 뷰로 효과를 검증했는가?
