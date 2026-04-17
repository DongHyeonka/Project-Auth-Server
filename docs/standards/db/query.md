# Query 기준

## 1. 목적

이 문서는 PostgreSQL에서 조회 쿼리를 어떤 기준으로 작성할지 정의한다.

이 문서의 목표는 다음과 같다.

- 쿼리가 의도한 결과 집합을 정확하게 반환하게 만든다
- 같은 의미의 쿼리라도 더 안전하고 해석 가능한 형태를 기본값으로 둔다
- planner가 최적화하기 쉬운 구조를 기본으로 선택한다
- JPA/Hibernate를 쓰더라도 실제로 실행되는 SQL 기준으로 판단하는 습관을 만든다

PostgreSQL 공식 문서는 SELECT의 처리 순서를 FROM → WHERE → GROUP BY/HAVING → SELECT list → DISTINCT → ORDER BY → LIMIT/OFFSET 순서로 설명한다. 따라서 query 기준은 “문법이 되느냐”보다 “어떤 단계에서 어떤 의미를 만들고 있는가”를 기준으로 세워야 한다.

## 2. 근거 수준

- Official: PostgreSQL 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL의 SELECT, table expressions, LIMIT/OFFSET, subquery expressions, aggregates, CTE, EXPLAIN 문서를 기반으로 작성한다. 특히 LIMIT의 비결정성, outer join에서 ON과 WHERE의 의미 차이, EXISTS/NOT IN의 null semantics, DISTINCT ON, UNION ALL, WITH materialization 규칙은 공식 문서로 직접 확인할 수 있다.

## 3. 기본 원칙

### 3.1 쿼리는 먼저 “정확한 결과 집합”을 정의해야 한다

성능 최적화는 중요하지만, 그보다 먼저 결과 집합의 의미가 흔들리지 않아야 한다. PostgreSQL은 ORDER BY가 없으면 결과 행 순서를 보장하지 않고, DISTINCT, GROUP BY, HAVING, LIMIT이 각각 다른 단계에서 의미를 바꾼다. 따라서 이 문서의 기본 원칙은 “빠른 쿼리”보다 “의도가 명확하고 재현 가능한 쿼리”를 먼저 만드는 것이다.

### 3.2 성능 판단은 추측이 아니라 실행 계획으로 검증한다

PostgreSQL planner는 쿼리 구조와 통계에 따라 sequential scan, index scan, bitmap index scan 등 서로 다른 접근 방식을 선택한다. 따라서 query 개선은 감으로 판단하지 않고 EXPLAIN, 가능하면 EXPLAIN ANALYZE로 확인해야 한다.

## 4. ORDER BY / LIMIT / OFFSET 기준

### 4.1 LIMIT / OFFSET / FETCH를 사용할 때는 항상 ORDER BY를 명시한다

PostgreSQL 공식 문서는 LIMIT을 사용할 때 결과 행을 **고유한 순서로 제약하는 ORDER BY**를 함께 두는 것이 중요하다고 설명한다. ORDER BY가 없으면 어떤 행이 “앞쪽 몇 개”인지 정의되지 않으며, LIMIT/OFFSET 값이 달라지면 planner가 다른 실행 계획을 택해 서로 다른 부분집합을 반환할 수 있다.

프로젝트 규칙:

- LIMIT, OFFSET, FETCH FIRST, DISTINCT ON이 있으면 ORDER BY를 기본 필수로 본다
- ORDER BY는 가능하면 결정적(unique) 순서가 되도록 마지막 tie-breaker까지 포함한다
- “현재는 우연히 같은 순서로 보인다”를 신뢰하지 않는다

### 4.2 ORDER BY의 tie-breaker를 명시한다

PostgreSQL은 ORDER BY의 왼쪽 표현식이 같으면 다음 표현식으로 비교하고, 모두 같으면 구현 의존 순서로 반환한다고 설명한다. 따라서 created_at DESC만으로는 동률이 생길 수 있고, 목록/최신 1건/상위 N건 쿼리에서는 id DESC 같은 보조 정렬 기준까지 포함하는 편이 안전하다.

프로젝트 규칙:

- 운영 API나 배치 기준 쿼리의 정렬은 tie-breaker까지 포함한다
- “최신 1건”, “최근 N건”, “top N” 같은 쿼리에서는 특히 tie-breaker를 생략하지 않는다

## 5. JOIN 기준

### 5.1 INNER JOIN은 명시적 JOIN 문법을 기본으로 사용한다

PostgreSQL은 inner join 조건을 WHERE에 쓰는 방식과 JOIN ... ON에 쓰는 방식이 동등할 수 있다고 설명한다. 하지만 실무에서는 명시적 JOIN ... ON이 관계를 더 분명하게 드러내고, join 조건과 후행 필터를 구분하기 쉽다.

프로젝트 규칙:

- 기본은 FROM a JOIN b ON ...
- join 조건은 ON
- join 후 결과 필터는 WHERE
- 오래된 comma join (FROM a, b WHERE ...)은 기본 금지

### 5.2 OUTER JOIN에서는 ON과 WHERE의 위치가 의미를 바꾼다

PostgreSQL 공식 문서는 outer join에서 ON 절은 “어떤 행이 매칭되는가”를 결정하고, WHERE 절은 join 결과가 만들어진 뒤에 적용된다고 설명한다. 따라서 LEFT JOIN의 오른쪽 테이블 조건을 WHERE에 두면 의도치 않게 inner join처럼 동작할 수 있다.

프로젝트 규칙:

- outer join의 매칭 조건은 ON
- unmatched row를 유지해야 하는데 오른쪽 테이블 조건을 WHERE로 내리지 않는다
- outer join을 썼다면 ON과 WHERE가 각각 무엇을 의미하는지 설명할 수 있어야 한다

### 5.3 USING은 허용하지만 NATURAL JOIN은 금지한다

PostgreSQL은 USING이 지정한 컬럼만 결합하므로 컬럼 변화에 비교적 안전한 반면, NATURAL JOIN은 두 테이블에 같은 이름의 컬럼이 새로 생기면 join 의미가 바뀔 수 있어 훨씬 위험하다고 명시한다.

프로젝트 규칙:

- 동일한 이름의 키 컬럼을 조인할 때 USING (col)은 허용
- NATURAL JOIN은 기본 금지
- 스키마 변경에 따라 silently 의미가 바뀌는 join을 허용하지 않는다

## 6. EXISTS / IN / NOT IN 기준

### 6.1 존재 여부 확인은 EXISTS를 우선 사용한다

PostgreSQL은 EXISTS가 “한 행이라도 반환되는지”만 확인하며, 일반적으로 전체를 끝까지 실행하지 않고 존재 여부를 판단할 만큼만 수행한다고 설명한다. 또한 공식 예시도 EXISTS (SELECT 1 ...)가 inner join과 비슷하지만 중복 매칭이 있어도 바깥 행을 한 번만 반환한다는 점을 보여 준다.

프로젝트 규칙:

- 존재 여부 확인은 COUNT(*) > 0보다 EXISTS를 우선
- join으로 row multiplication을 만든 뒤 DISTINCT로 지우는 패턴보다 EXISTS를 우선 검토
- EXISTS 서브쿼리의 output list는 관례적으로 SELECT 1

### 6.2 배제 조건은 NOT EXISTS를 기본 검토한다

PostgreSQL은 NOT IN (subquery)에서 오른쪽 결과에 null이 하나라도 섞이면 결과가 true가 아니라 null이 될 수 있다고 설명한다. 이 null semantics는 실무에서 자주 실수를 만든다. 따라서 subquery 쪽 null 가능성을 완전히 통제하지 못하면 anti-join은 NOT EXISTS가 더 안전한 기본값이다.

프로젝트 규칙:

- anti-join 기본값은 NOT EXISTS
- NOT IN은 서브쿼리 값이 null이 아님을 명확히 보장할 때만 제한적으로 사용
- null semantics를 설명할 수 없으면 NOT IN을 사용하지 않는다

## 7. GROUP BY / HAVING / Aggregate 기준

### 7.1 행 필터는 WHERE, 그룹 필터는 HAVING

PostgreSQL은 WHERE가 grouping 전에 개별 행을 필터링하고, HAVING은 grouping 후 그룹 행을 필터링한다고 설명한다. 튜토리얼 문서도 aggregate가 필요 없는 조건은 WHERE에 두는 편이 더 효율적이라고 명시한다.

프로젝트 규칙:

- aggregate와 무관한 조건은 WHERE
- aggregate 결과를 기준으로 한 조건만 HAVING
- row filter를 HAVING으로 올려서 grouping 비용을 불필요하게 만들지 않는다

### 7.2 조건부 집계는 FILTER를 우선 검토한다

PostgreSQL은 aggregate input을 FILTER (WHERE ...)로 개별 aggregate마다 따로 제한할 수 있다고 설명한다. 같은 grouped query 안에서 여러 조건부 카운트/합계를 계산해야 할 때 FILTER는 의미를 분명하게 만든다.

프로젝트 규칙:

- 같은 그룹에서 여러 조건부 aggregate가 필요하면 FILTER 우선 검토
- SUM(CASE WHEN ... THEN 1 ELSE 0 END) 패턴은 표현력이 부족할 때만 사용
- aggregate별 조건을 명확하게 드러내는 쪽을 선호한다

### 7.3 순서가 중요한 aggregate는 내부 ORDER BY를 명시한다

PostgreSQL은 array_agg, json_agg, jsonb_agg, string_agg, xmlagg 같은 aggregate는 입력 순서에 따라 결과가 달라질 수 있다고 설명한다. 따라서 결과 순서가 계약이라면 aggregate 안쪽 ORDER BY를 명시해야 한다.

프로젝트 규칙:

- 순서가 중요한 array_agg / json_agg / string_agg는 내부 ORDER BY를 명시
- “현재 우연히 원하는 순서로 보인다”를 신뢰하지 않는다

## 8. DISTINCT / DISTINCT ON / UNION 기준

### 8.1 DISTINCT는 의미가 필요할 때만 사용한다

PostgreSQL은 SELECT DISTINCT가 중복 행을 제거한다고 설명한다. 즉 DISTINCT는 단순 성능 옵션이 아니라 결과 의미를 바꾸는 연산이다. 따라서 join이 잘못되어 생긴 row multiplication을 가리기 위한 반사적 DISTINCT는 기본 금지다. 이는 공식 동작 위에 얹는 best practice다.

프로젝트 규칙:

- DISTINCT를 쓰면 “어떤 중복을 왜 제거하는가”를 설명할 수 있어야 한다
- join multiplicity 문제를 DISTINCT로 덮지 않는다
- 중복이 생기지 않도록 join 또는 EXISTS 구조를 먼저 바로잡는다

### 8.2 one-row-per-group이 필요하면 DISTINCT ON을 제한적으로 사용한다

PostgreSQL의 DISTINCT ON은 같은 key 그룹에서 첫 행 하나만 남기며, 어떤 행이 “첫 행”인지 예측 가능하게 하려면 ORDER BY가 필요하다. 또한 DISTINCT ON 식은 ORDER BY의 leftmost expressions와 일치해야 한다.

프로젝트 규칙:

- “최신 1건 per key” 같은 PostgreSQL 특화 패턴에는 DISTINCT ON 허용
- 반드시 ORDER BY와 함께 사용
- DISTINCT ON 없이도 window function이 더 명확하면 그쪽을 우선 검토

### 8.3 deduplication이 불필요하면 UNION ALL을 기본으로 한다

PostgreSQL은 UNION이 중복 제거를 수행하고, UNION ALL은 그 제거를 하지 않기 때문에 보통 훨씬 빠르다고 설명한다.

프로젝트 규칙:

- 두 결과 집합을 단순 합치기만 하면 UNION ALL
- 진짜 set semantics가 필요할 때만 UNION
- “일단 UNION”을 기본값으로 두지 않는다

## 9. CTE / 서브쿼리 기준

### 9.1 CTE는 가독성을 위해 사용하되, 항상 최적화 이점을 준다고 가정하지 않는다

PostgreSQL은 non-recursive, side-effect-free CTE가 부모 쿼리에서 한 번만 참조되면 folding될 수 있지만, 여러 번 참조되면 기본적으로 materialized 될 수 있다고 설명한다. 즉 CTE는 “가독성 도구”이지 자동 성능 향상 도구가 아니다.

프로젝트 규칙:

- 복잡한 쿼리 단계 분해를 위해 CTE 사용 가능
- 하지만 CTE를 썼다는 이유만으로 planner가 항상 최적으로 밀어 넣는다고 가정하지 않는다
- 성능 민감 쿼리는 CTE 도입 전후를 EXPLAIN으로 확인한다

### 9.2 MATERIALIZED / NOT MATERIALIZED는 의도를 갖고 선택한다

PostgreSQL은 NOT MATERIALIZED가 parent restrictions를 아래로 밀어 넣어 이득을 줄 수 있지만, 반대로 비싼 계산을 여러 번 반복하게 만들 수도 있다고 설명한다. 반면 materialization은 중복 계산을 막을 수 있다.

프로젝트 규칙:

- NOT MATERIALIZED는 predicate pushdown 이득이 분명할 때만 사용
- 비싼 표현식을 재사용하는 CTE는 materialization이 더 나을 수 있음을 고려
- 힌트처럼 무심코 붙이지 않는다

## 10. COUNT 기준

### 10.1 COUNT(*)는 공짜가 아니다

PostgreSQL 공식 문서는 전체 테이블에 대한 count(*)가 테이블 크기에 비례하는 비용을 요구하며, 전체 테이블 또는 모든 행을 포함한 인덱스를 스캔해야 할 수 있다고 설명한다.

프로젝트 규칙:

- 존재 여부 확인에는 COUNT(*) > 0 대신 EXISTS
- 목록 API나 배치에서 total count는 정말 필요한 경우에만 계산
- count가 비싸다는 사실을 전제로 설계한다

## 11. 검증 기준

### 11.1 성능 이슈가 있는 query는 EXPLAIN (ANALYZE)로 검증한다

PostgreSQL은 실행 계획이 scan node와 join node의 트리로 표현되며, planner가 sequential scan, index scan, bitmap index scan 등을 선택한다고 설명한다. 따라서 query 기준 문서에서의 모든 성능 판단은 실행 계획 확인을 전제로 한다.

프로젝트 규칙:

- 느린 query 개선은 EXPLAIN (ANALYZE, BUFFERS)를 기본 검토
- 인덱스 추가와 query 재작성은 전후 계획을 비교
- “이 쿼리가 빠를 것 같다” 수준의 추측으로 merge하지 않는다

## 12. 금지 규칙

다음은 기본 금지다.

- LIMIT/OFFSET을 ORDER BY 없이 사용
- outer join의 오른쪽 테이블 필터를 무심코 WHERE에 내려서 의미를 바꾸는 것
- NATURAL JOIN 사용
- 존재 여부 확인을 COUNT(*) > 0로 처리
- null 가능성이 있는 subquery에 NOT IN 사용
- row filter를 HAVING으로 올려서 grouping 후 필터링
- join 중복을 DISTINCT로 숨기기
- deduplication이 불필요한데 UNION 사용
- CTE를 성능 힌트처럼 기계적으로 사용
- 성능 논쟁을 EXPLAIN 없이 종료

이 금지 규칙은 PostgreSQL 공식 의미 위에 얹는 실무 best practice이며, 특히 운영 장애를 만들기 쉬운 query semantics 오류를 줄이기 위한 프로젝트 권장안이다.

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 쿼리는 결과 집합 의미를 명확히 설명할 수 있는가?
- LIMIT/OFFSET이 있다면 결정적 ORDER BY가 있는가?
- outer join에서 ON과 WHERE가 의도대로 배치되었는가?
- 존재 여부 확인에 EXISTS를 검토했는가?
- anti-join에서 NOT EXISTS가 더 안전한지 검토했는가?
- row filter와 group filter를 WHERE/HAVING으로 올바르게 나눴는가?
- 순서가 중요한 aggregate에 내부 ORDER BY를 명시했는가?
- DISTINCT가 실제 의미 요구인지, join 문제를 가리는 것인지 구분했는가?
- UNION ALL로 충분한데 UNION을 쓰고 있지 않은가?
- CTE materialization 규칙을 이해하고 있는가?
- 성능 판단을 EXPLAIN (ANALYZE)로 검증했는가?
