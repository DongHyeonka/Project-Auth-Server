# Pagination Query 기준

## 1. 목적

이 문서는 PostgreSQL에서 목록 조회를 페이지 단위로 가져오는 query를 어떤 기준으로 설계할지 정의한다.

이 문서의 목표는 다음과 같다.

- 페이지 경계가 결정적이고 재현 가능한 query를 기본값으로 둔다
- `LIMIT`/`OFFSET`와 keyset/cursor 방식의 적용 조건을 구분한다
- 페이지 query와 count query를 분리해서 생각하게 만든다
- 정렬 조건과 인덱스 구성을 pagination query와 맞물려 설계하게 만든다

PostgreSQL은 `LIMIT`/`OFFSET`을 사용할 때 `ORDER BY`로 결과를 고유한 순서로 제약하는 것이 중요하다고 설명하고, 큰 `OFFSET`은 건너뛴 row도 서버 내부에서 계산해야 하므로 비효율적일 수 있다고 설명한다. 또한 `ORDER BY ... LIMIT n`은 정렬과 인덱스 설계를 함께 볼 때 특히 중요하다고 설명한다.

## 2. 근거 수준

- Official: PostgreSQL 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 실무 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL의 `LIMIT`/`OFFSET`, `ORDER BY`, multicolumn index, index ordering, row constructor comparison, planner statistics 문서를 기준으로 작성한다. PostgreSQL은 `LIMIT`/`OFFSET`의 비결정성, B-tree만의 ordered scan, 멀티 컬럼 B-tree의 leftmost 규칙, row constructor `<`/`>` 비교의 좌→우 비교 규칙을 공식적으로 설명한다.

## 3. 기본 원칙

### 3.1 pagination query는 “목록 일부”가 아니라 “순서가 정의된 연속 구간”을 가져와야 한다

페이지네이션은 단순히 `LIMIT n`을 붙여 일부 row를 가져오는 것이 아니라, 정렬 기준이 명확한 전체 순서 위에서 연속 구간을 잘라 오는 계약이어야 한다. PostgreSQL은 `ORDER BY`가 없으면 결과 행 순서를 보장하지 않고, `LIMIT`/`OFFSET` 값이 달라지면 planner가 다른 plan을 선택해 서로 다른 부분집합을 반환할 수 있다고 설명한다.

### 3.2 정렬 기준은 결정적이어야 한다

페이지 경계가 흔들리지 않으려면 `ORDER BY`가 tie-breaker까지 포함한 결정적(unique) 순서여야 한다. PostgreSQL은 `LIMIT`과 함께 쓸 때 `ORDER BY`가 결과를 unique order로 제약하는 것이 중요하다고 설명한다. 또한 B-tree 인덱스는 실제로 정렬된 출력 자체를 제공할 수 있고, 내부적으로 table TID를 동률 tie-breaker처럼 다루지만, 애플리케이션 query 계약은 이를 암묵적으로 의존하지 말고 명시적 정렬 컬럼으로 닫는 편이 안전하다.

### 3.3 pagination 방식은 접근 패턴에 맞게 고른다

관리자 화면처럼 “몇 페이지든 자유롭게 점프”가 중요하면 `LIMIT`/`OFFSET`이 단순할 수 있고, 무한 스크롤·타임라인·깊은 페이지 이동이 많으면 keyset/cursor 방식이 더 적합하다. 이는 PostgreSQL 문서가 직접 “keyset pagination”이라는 이름으로 규정하지는 않지만, 큰 `OFFSET`의 비효율성과 B-tree ordered scan, row constructor comparison 규칙을 함께 보면 자연스럽게 도출되는 운영 best practice다.

## 4. OFFSET 기반 페이지네이션 기준

### 4.1 LIMIT/OFFSET은 얕은 페이지와 임의 페이지 점프가 필요한 경우에 사용한다

PostgreSQL은 `LIMIT`/`OFFSET`이 결과 일부를 가져오는 기본 수단이라고 설명한다. 프로젝트에서는 백오피스 표, 관리 화면, 데이터 탐색처럼 “3페이지로 점프”, “27페이지로 이동” 같은 요구가 실제로 중요한 경우 `LIMIT`/`OFFSET`을 허용한다. 단, 이는 얕은 페이지를 전제로 할 때 가장 자연스럽다.

### 4.2 LIMIT/OFFSET에는 항상 결정적 ORDER BY를 함께 둔다

PostgreSQL은 `LIMIT` 사용 시 `ORDER BY`가 없으면 예측 불가능한 subset을 얻게 된다고 명시한다. 따라서 프로젝트에서는 `LIMIT`/`OFFSET` query에 `ORDER BY`를 필수로 보고, 가능하면 마지막 tie-breaker까지 포함한다. 예를 들어 `ORDER BY created_at DESC, id DESC`처럼 정렬한다.

### 4.3 큰 OFFSET은 기본적으로 비효율적이라고 본다

PostgreSQL은 `OFFSET`으로 건너뛴 row도 서버 내부에서는 계산되어야 하므로 큰 `OFFSET`이 비효율적일 수 있다고 설명한다. 따라서 프로젝트에서는 page number가 깊어질수록 `OFFSET` 기반 pagination 성능이 떨어질 수 있음을 기본 가정으로 둔다. “10,000번째 페이지” 같은 요구는 `LIMIT`/`OFFSET`의 기본 사용처가 아니다.

### 4.4 데이터가 계속 바뀌는 목록에서 OFFSET은 경계가 흔들릴 수 있다

PostgreSQL은 `LIMIT`/`OFFSET` 값이 달라지면 다른 subset이 선택될 수 있고, `ORDER BY`가 없으면 특히 비일관적이라고 설명한다. 여기에 일반적인 `READ COMMITTED` 읽기 특성까지 결합하면, 요청 사이에 insert/delete/update가 일어나는 목록에서는 page boundary가 움직여 중복/누락처럼 보이는 사용자 경험이 생길 수 있다. 이 문장은 PostgreSQL의 `LIMIT`/`OFFSET` semantics와 기본 읽기 모델을 결합한 실무 해석이다.

## 5. keyset / cursor 페이지네이션 기준

### 5.1 깊은 페이지, 무한 스크롤, 시간순 피드는 keyset/cursor를 기본 검토한다

PostgreSQL 공식 문서는 큰 `OFFSET`이 비효율적일 수 있다고 설명하고, B-tree 인덱스는 `ORDER BY ... LIMIT n`에서 정렬된 앞부분을 직접 빠르게 반환할 수 있다고 설명한다. 이 둘을 종합하면, “마지막으로 본 정렬 키 이후의 다음 n건”을 가져오는 keyset/cursor 방식이 깊은 페이지나 연속 스크롤에 더 잘 맞는다. 이는 공식 문서 위에 얹는 best practice다.

### 5.2 keyset pagination은 ORDER BY와 동일한 의미의 seek 조건을 사용한다

keyset/cursor pagination은 보통 “마지막으로 본 정렬 키보다 뒤(또는 앞)에 있는 row”를 가져온다. PostgreSQL은 row constructor comparison이 `<`, `<=`, `>`, `>=`를 지원하고, 왼쪽에서 오른쪽으로 비교를 진행한다고 설명한다. 따라서 `(created_at, id)`처럼 정렬한 경우, 같은 순서 의미를 `WHERE (created_at, id) < (:lastCreatedAt, :lastId)` 같은 형태로 표현할 수 있다. 이는 PostgreSQL row comparison 기능을 pagination에 적용한 프로젝트 권장안이다.

### 5.3 keyset 정렬에도 tie-breaker는 필수다

정렬 컬럼 하나만으로는 동률이 생길 수 있으므로, keyset/cursor도 마지막 tie-breaker까지 포함해야 한다. PostgreSQL의 row comparison은 좌→우 비교이므로, `(created_at, id)`처럼 동률 해소용 유니크 컬럼을 마지막에 두면 query 의미가 명확해진다. 프로젝트에서는 timestamp 단독 cursor를 기본 금지하고, 항상 유니크 tie-breaker를 붙인다.

### 5.4 keyset pagination은 “임의 페이지 번호 이동”보다 “다음/이전 탐색”에 적합하다

PostgreSQL 공식 문서는 cursor-style API pagination을 직접 설계해 주지는 않지만, 큰 `OFFSET` 비효율과 ordered index scan의 장점을 분명히 설명한다. 이 특성상 keyset/cursor는 “page 57로 점프”보다 “다음 20건”, “이전 20건” 같은 연속 탐색에 더 잘 맞는다. 프로젝트에서는 keyset/cursor를 page number UI에 억지로 맞추기보다, 연속 탐색형 계약에 사용한다.

## 6. 정렬 기준과 인덱스 기준

### 6.1 pagination query의 정렬 기준은 인덱스 설계와 함께 정한다

PostgreSQL은 B-tree만 ordered output을 만들 수 있고, `ORDER BY ... LIMIT n`에서는 정렬을 만족하는 인덱스가 있으면 앞의 n개를 직접 가져올 수 있다고 설명한다. 따라서 pagination query는 정렬 기준을 먼저 정하고, 그 정렬을 실제로 공급할 인덱스를 같이 설계해야 한다.

### 6.2 멀티 컬럼 인덱스는 필터와 정렬의 앞부분을 함께 고려한다

PostgreSQL은 multicolumn B-tree가 leftmost column 제약에서 가장 효율적이고, leading column의 equality와 그 다음 첫 non-equality column의 inequality가 스캔 범위를 가장 잘 줄인다고 설명한다. 따라서 pagination query가 `WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC, id DESC LIMIT 20`라면, 인덱스도 `(tenant_id, created_at DESC, id DESC)`처럼 고정 필터 + 정렬 키 순서를 함께 검토하는 것이 자연스럽다.

### 6.3 단일 컬럼 DESC 인덱스를 기계적으로 만들지는 않는다

PostgreSQL은 B-tree가 forward/backward scan을 모두 지원하므로, 단일 컬럼에서는 별도 DESC 인덱스가 보통 유용하지 않다고 설명한다. 다만 멀티 컬럼에서 혼합 정렬 방향이 있으면 별도 정렬 지정이 의미를 가질 수 있다. 프로젝트에서는 pagination query의 정렬 방향이 단순 1컬럼이면 먼저 일반 B-tree로 충분한지 검토한다.

### 6.4 active row pagination은 soft delete predicate와 인덱스를 맞춘다

soft delete를 쓰는 테이블에서 일반 목록은 `deleted_at IS NULL`이 기본 predicate여야 하고, PostgreSQL partial index는 query의 `WHERE`가 그 predicate를 함의할 때 가장 자연스럽게 사용된다. 따라서 active row pagination query는 soft delete predicate를 항상 포함하고, 필요하면 `WHERE deleted_at IS NULL` partial index와 맞춘다. 이 원칙은 PostgreSQL partial index semantics를 pagination에 적용한 프로젝트 규칙이다.

## 7. count 기준

### 7.1 page query와 total count query는 분리해서 본다

pagination에서는 “현재 페이지 20건 조회”와 “전체 몇 건인지 계산”이 서로 다른 비용 구조를 가진다. PostgreSQL은 `count(*)`가 입력 row 수를 세는 aggregate라고 설명하고, planner statistics인 `reltuples`는 VACUUM/ANALYZE 기반의 근사치라고 설명한다. 따라서 프로젝트에서는 page query와 total count를 하나의 당연한 세트로 보지 않고, 정말 필요한 경우에만 별도 count query를 수행한다. exact count가 필요 없는 화면이라면 next page 존재 여부만 계산하는 방식도 허용한다.

### 7.2 대규모 목록에서는 “정확한 총건수”를 항상 요구하지 않는다

PostgreSQL 공식 문서상 planner 통계는 근사치이며, `count(*)`는 row 수를 실제로 집계하는 aggregate다. 이 특성을 고려하면, 아주 큰 목록에서 모든 요청마다 정확한 total count를 함께 구하는 것은 기본값으로 보기 어렵다. 프로젝트에서는 화면 요구가 약하면 “더 보기 가능 여부” 또는 근사치 메타데이터를 우선 검토한다. 이 부분은 공식 문서의 aggregate/statistics semantics 위에 얹는 운영 best practice다.

## 8. 페이지 크기 기준

### 8.1 page size는 API 계약으로 제한한다

PostgreSQL이 직접 “API page size upper bound”를 규정하지는 않지만, `ORDER BY ... LIMIT`가 적은 수의 row를 빠르게 가져오는 데 특히 유리하다고 설명하는 반면, 더 큰 범위를 읽을수록 인덱스 이점은 약해질 수 있다고 설명한다. 프로젝트에서는 무제한 `LIMIT`을 허용하지 않고, endpoint별로 허용 가능한 최대 page size를 계약으로 둔다. 이 규칙은 PostgreSQL ordered scan 특성 위에 얹는 best practice다.

### 8.2 page size 변경은 cursor 계약에도 영향을 준다

keyset/cursor pagination에서 cursor는 정렬 키와 page boundary 의미를 담는다. 따라서 client가 임의로 page size를 크게 바꾸면 응답 shape와 캐시/UX 의미가 달라질 수 있다. PostgreSQL이 이를 직접 규정하지는 않지만, pagination query를 안정적인 계약으로 유지하려면 page size도 정렬/seek 조건과 함께 관리해야 한다. 이 부분은 공식 정렬/limit semantics 위에 얹는 프로젝트 권장안이다.

## 9. 문서 경계

이 문서는 페이지 단위 목록 query 설계를 다룬다.

다음 내용은 별도 문서에서 확장한다.

- 일반 query semantics와 `WHERE`/`HAVING`/`DISTINCT` 기준
- JPA fetch join과 pagination 충돌
- soft delete 기본 조회 계약
- cursor token 인코딩과 API 응답 포맷
- 검색 결과 캐싱 전략

현재 문서 체계에서도 query, jpa-fetch-strategy, n-plus-one, soft-delete는 이미 별도 주제로 분리되어 있다.

## 10. 금지 규칙

다음은 기본 금지다.

- `LIMIT`/`OFFSET`을 `ORDER BY` 없이 사용하는 것
- tie-breaker 없는 비결정적 정렬로 페이지를 자르는 것
- 깊은 페이지 요구에 무조건 `OFFSET`만 사용하는 것
- keyset/cursor에서 정렬 기준과 seek 조건이 다른 것
- timestamp 단독 cursor처럼 동률 처리가 불명확한 설계
- page query와 count query 비용을 같은 것으로 보는 것
- 큰 목록에서 매 요청마다 exact total count를 기본 강제하는 것
- soft delete predicate가 필요한 테이블에서 active-row 조건 없이 페이지를 자르는 것
- pagination query 인덱스를 정렬 기준과 무관하게 만드는 것

이 금지 규칙은 PostgreSQL의 `LIMIT`/`OFFSET`, `ORDER BY`, B-tree ordered scan, multicolumn index, row comparison semantics를 실무 규칙으로 압축한 것이다.

## 11. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 pagination query의 전체 순서가 결정적으로 정의되어 있는가?
- `LIMIT`/`OFFSET`이라면 tie-breaker까지 포함한 `ORDER BY`가 있는가?
- 깊은 페이지/무한 스크롤이라면 keyset/cursor를 검토했는가?
- keyset이라면 `ORDER BY`와 seek 조건이 같은 의미를 가지는가?
- 정렬 기준과 인덱스 구성이 맞물려 있는가?
- soft delete 테이블이라면 active-row predicate가 query와 index에 일관되게 반영되어 있는가?
- page query와 total count query를 분리해서 설계했는가?
- exact total count가 정말 필요한지 확인했는가?
- 최대 page size가 API 계약으로 제한되어 있는가?
