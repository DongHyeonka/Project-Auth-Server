# Pagination Query 예시

## 좋은 예시

### 예시 1. 얕은 관리자 목록은 결정적 ORDER BY + LIMIT/OFFSET으로 처리한다

```sql
SELECT id, email, created_at
FROM auth.users
WHERE deleted_at IS NULL
ORDER BY created_at DESC, id DESC
LIMIT :limit
OFFSET :offset;
```

**좋은 이유:**

- `LIMIT/OFFSET`을 쓰면서 결정적 `ORDER BY`를 함께 둔다
- `created_at` 동률을 `id`로 해소한다
- active-row predicate가 soft delete 계약과 맞는다

PostgreSQL은 `LIMIT/OFFSET`에 `ORDER BY`가 없으면 예측 불가능한 subset을 얻게 된다고 설명하고, 큰 `OFFSET`은 비효율적일 수 있다고 설명한다.

### 예시 2. 무한 스크롤은 keyset/cursor 방식으로 설계한다

```sql
SELECT id, created_at, title
FROM board.posts
WHERE deleted_at IS NULL
  AND (created_at, id) < (:lastCreatedAt, :lastId)
ORDER BY created_at DESC, id DESC
LIMIT :limit;
```

**좋은 이유:**

- 깊은 페이지에서도 큰 `OFFSET`을 피할 수 있다
- 정렬 기준과 seek 조건이 같은 의미를 가진다
- `(created_at, id)`가 tie-breaker까지 포함한 cursor 역할을 한다

PostgreSQL은 row constructor comparison의 `<`, `>`가 좌→우 비교로 동작한다고 설명하고, `ORDER BY ... LIMIT n`에서 B-tree ordered scan이 유리할 수 있다고 설명한다. 이 예시는 그 두 기능을 pagination에 적용한 전형적 패턴이다.

### 예시 3. 필터와 정렬이 함께 있는 페이지 query는 인덱스와 같이 설계한다

```sql
SELECT id, tenant_id, status, created_at
FROM ordering.orders
WHERE tenant_id = :tenantId
  AND status = 'READY'
ORDER BY created_at DESC, id DESC
LIMIT :limit;
```

```sql
CREATE INDEX ix_orders__tenant_status_created_at_id
    ON ordering.orders (tenant_id, status, created_at DESC, id DESC);
```

**좋은 이유:**

- leading equality filter 뒤에 정렬 키를 배치한다
- pagination query와 인덱스가 같은 access pattern을 공유한다
- 적은 수의 앞 row를 직접 가져오기에 유리하다

PostgreSQL은 multicolumn B-tree가 leading equality와 그 다음 inequality/정렬 문맥에서 가장 효율적이라고 설명하고, `ORDER BY ... LIMIT n`에서 정렬을 만족하는 인덱스가 특히 유용하다고 설명한다.

### 예시 4. total count가 꼭 필요할 때만 별도 query로 분리한다

```sql
SELECT id, email, created_at
FROM auth.users
WHERE deleted_at IS NULL
ORDER BY created_at DESC, id DESC
LIMIT :limit
OFFSET :offset;
```

```sql
SELECT count(*)
FROM auth.users
WHERE deleted_at IS NULL;
```

**좋은 이유:**

- 페이지 조회와 총건수 계산을 명시적으로 분리한다
- 목록 일부 조회와 전체 집계의 비용을 섞지 않는다
- count가 필요 없는 API에서는 두 번째 query를 생략할 수 있다

PostgreSQL은 `count(*)`가 입력 row 수를 계산하는 aggregate라고 설명하고, planner statistics인 `reltuples`는 근사치라고 설명한다.

### 예시 5. 다음 페이지 존재 여부만 필요하면 한 건 더 가져온다

```sql
SELECT id, created_at, title
FROM board.posts
WHERE deleted_at IS NULL
  AND (created_at, id) < (:lastCreatedAt, :lastId)
ORDER BY created_at DESC, id DESC
LIMIT :limitPlusOne;
```

**좋은 이유:**

- exact total count 없이도 next page 존재 여부를 계산할 수 있다
- 큰 목록에서 count 비용을 매번 강제하지 않는다
- keyset/cursor 방식과 잘 맞는다

PostgreSQL 공식 문서는 `LIMIT`이 결과 일부를 가져오는 기본 도구임을 설명하고, 큰 `OFFSET`이 비효율적일 수 있음을 설명한다. 이 예시는 total count를 피하는 실무 패턴이다.

## 나쁜 예시

### 예시 1. LIMIT/OFFSET을 정렬 없이 사용한다

```sql
SELECT id, email
FROM auth.users
LIMIT :limit
OFFSET :offset;
```

**나쁜 이유:**

- 어떤 row 집합을 잘라 오는지 정의되지 않는다
- `LIMIT/OFFSET` 값에 따라 plan과 결과 subset이 달라질 수 있다
- 페이지 계약이 성립하지 않는다

PostgreSQL은 `ORDER BY` 없이 `LIMIT`을 쓰면 예측 불가능한 subset을 얻게 된다고 명시한다.

### 예시 2. tie-breaker 없는 정렬로 페이지를 자른다

```sql
SELECT id, created_at, title
FROM board.posts
ORDER BY created_at DESC
LIMIT :limit
OFFSET :offset;
```

**나쁜 이유:**

- 같은 `created_at`을 가진 row의 상대 순서가 API 계약상 명시되지 않는다
- 페이지 경계가 흔들리거나 중복/누락처럼 보일 수 있다
- `id` 같은 유니크 tie-breaker가 필요하다

PostgreSQL은 `LIMIT`과 함께 사용할 때 `ORDER BY`가 결과를 unique order로 제약하는 것이 중요하다고 설명한다.

### 예시 3. 깊은 페이지를 큰 OFFSET으로 계속 읽는다

```sql
SELECT id, created_at, title
FROM board.posts
WHERE deleted_at IS NULL
ORDER BY created_at DESC, id DESC
LIMIT 20
OFFSET 200000;
```

**나쁜 이유:**

- 건너뛴 200,000 row도 서버 내부에서 계산해야 한다
- 깊은 페이지로 갈수록 비용이 커질 수 있다
- 이런 요구는 keyset/cursor 방식이 더 적합하다

PostgreSQL은 `OFFSET`으로 건너뛴 row도 내부에서 계산되어야 하므로 큰 `OFFSET`이 비효율적일 수 있다고 설명한다.

### 예시 4. keyset인데 seek 조건과 정렬이 맞지 않는다

```sql
SELECT id, created_at, title
FROM board.posts
WHERE id < :lastId
ORDER BY created_at DESC, id DESC
LIMIT :limit;
```

**나쁜 이유:**

- `ORDER BY`는 `(created_at, id)` 의미인데 seek 조건은 `id`만 본다
- 페이지 경계가 정렬 의미와 어긋난다
- keyset/cursor는 정렬 기준과 동일한 키 의미를 써야 한다

PostgreSQL의 row comparison은 여러 정렬 키를 좌→우로 비교할 수 있으므로, 이런 경우 `(created_at, id)` 형태가 더 자연스럽다.

### 예시 5. soft delete 테이블인데 active-row predicate 없이 페이지를 자른다

```sql
SELECT id, email, created_at
FROM auth.users
ORDER BY created_at DESC, id DESC
LIMIT :limit;
```

**나쁜 이유:**

- 삭제 row가 일반 목록에 섞일 수 있다
- active-row partial index와도 잘 맞지 않는다
- soft delete 기본 조회 계약을 깨뜨린다

PostgreSQL partial index는 query의 WHERE가 predicate를 함의할 때 가장 자연스럽게 사용되므로, soft delete 테이블은 active-row predicate를 일관되게 포함해야 한다.

### 예시 6. 페이지 query와 exact count를 항상 묶는다

```sql
SELECT id, email, created_at
FROM auth.users
WHERE deleted_at IS NULL
ORDER BY created_at DESC, id DESC
LIMIT :limit
OFFSET :offset;

SELECT count(*)
FROM auth.users
WHERE deleted_at IS NULL;
```

**나쁜 이유:**

- 총건수가 항상 필요한지 검토하지 않는다
- 목록 일부 조회와 전체 집계를 무조건 함께 수행한다
- 큰 목록에서는 불필요한 비용이 될 수 있다

PostgreSQL은 `count(*)`가 입력 row 수를 계산하는 aggregate이고, planner statistics는 근사치라고 설명한다. exact total count는 별도 비용을 가진다고 보고 설계해야 한다.
