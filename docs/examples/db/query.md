# Query 예시

## 좋은 예시

### 예시 1. LIMIT에는 결정적 ORDER BY를 함께 둔다

```sql
SELECT id, user_id, created_at
FROM auth.sessions
WHERE user_id = :userId
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

**좋은 이유:**

- LIMIT 결과가 어떤 20건인지 결정적으로 정의된다
- created_at 동률에서도 id가 tie-breaker가 된다
- 같은 query를 반복 실행하거나 페이지를 넘겨도 의미가 흔들리지 않는다

PostgreSQL은 LIMIT 사용 시 ORDER BY가 없으면 예측 불가능한 부분집합을 얻게 된다고 설명한다.

### 예시 2. LEFT JOIN의 매칭 조건은 ON에 둔다

```sql
SELECT u.id, u.email, s.id AS active_session_id
FROM auth.users u
LEFT JOIN auth.sessions s
  ON s.user_id = u.id
 AND s.revoked_at IS NULL
WHERE u.deleted_at IS NULL;
```

**좋은 이유:**

- “사용자 전체를 유지하면서 revoked 되지 않은 세션만 매칭”이라는 의미가 정확하다
- 오른쪽 조건을 ON에 두어 outer join 의미를 유지한다
- 사용자 필터와 join 매칭 조건이 분리되어 읽기 쉽다

PostgreSQL은 outer join에서 ON과 WHERE의 위치가 결과를 다르게 만든다고 명시한다.

### 예시 3. 존재 여부 확인은 EXISTS로 처리한다

```sql
SELECT u.id, u.email
FROM auth.users u
WHERE EXISTS (
    SELECT 1
    FROM auth.user_roles ur
    WHERE ur.user_id = u.id
      AND ur.role_name = 'ADMIN'
);
```

**좋은 이유:**

- “ADMIN role이 하나라도 있으면 됨”이라는 의미가 직접적이다
- role이 여러 개여도 바깥 사용자 row가 증폭되지 않는다
- COUNT(*) > 0보다 존재 여부 의도를 더 잘 드러낸다

PostgreSQL은 EXISTS가 행 존재 여부만 판단하며, 일반적으로 전부 끝까지 수행하지 않는다고 설명한다.

### 예시 4. anti-join은 NOT EXISTS를 사용한다

```sql
SELECT u.id, u.email
FROM auth.users u
WHERE NOT EXISTS (
    SELECT 1
    FROM auth.sessions s
    WHERE s.user_id = u.id
      AND s.revoked_at IS NULL
);
```

**좋은 이유:**

- null semantics 함정 없이 “활성 세션이 없는 사용자”를 표현한다
- NOT IN보다 더 안전한 기본값이다
- anti-join 의도가 분명하다

PostgreSQL은 NOT IN에 null이 섞이면 결과가 true가 아니라 null이 될 수 있다고 설명한다.

### 예시 5. row filter는 WHERE, 조건부 집계는 FILTER를 사용한다

```sql
SELECT
    user_id,
    count(*) FILTER (WHERE revoked_at IS NULL) AS active_count,
    count(*) FILTER (WHERE revoked_at IS NOT NULL) AS revoked_count
FROM auth.sessions
WHERE created_at >= :from
GROUP BY user_id;
```

**좋은 이유:**

- 기간 제한은 row filter이므로 WHERE
- 집계별 조건은 FILTER
- grouped query의 의미가 분명하다

PostgreSQL은 WHERE와 HAVING의 역할이 다르고, aggregate input을 FILTER로 제한할 수 있다고 설명한다.

### 예시 6. one-row-per-group에는 DISTINCT ON을 의도적으로 사용한다

```sql
SELECT DISTINCT ON (user_id)
    user_id,
    id,
    created_at
FROM auth.sessions
WHERE revoked_at IS NULL
ORDER BY user_id, created_at DESC, id DESC;
```

**좋은 이유:**

- 사용자별 최신 활성 세션 1건이라는 의미가 분명하다
- DISTINCT ON (user_id)와 ORDER BY user_id, ...가 맞춰져 있다
- 어떤 row를 남길지 예측 가능하다

PostgreSQL은 DISTINCT ON의 first row는 ORDER BY 없이는 예측 불가능하며, DISTINCT ON 식은 ORDER BY의 leftmost expressions와 맞아야 한다고 설명한다.

### 예시 7. deduplication이 불필요하면 UNION ALL을 사용한다

```sql
SELECT user_id, created_at, 'LOGIN' AS event_type
FROM auth.login_events
WHERE created_at >= :from

UNION ALL

SELECT user_id, created_at, 'LOGOUT' AS event_type
FROM auth.logout_events
WHERE created_at >= :from;
```

**좋은 이유:**

- 두 이벤트 집합을 단순 병합하는 요구다
- 중복 제거가 필요하지 않다
- UNION보다 의미와 비용이 더 적절하다

PostgreSQL은 UNION ALL이 중복 제거를 하지 않으므로 보통 더 빠르다고 설명한다.

### 예시 8. CTE는 단계 분해에 쓰되, 필요하면 NOT MATERIALIZED를 명시한다

```sql
WITH recent_sessions AS NOT MATERIALIZED (
    SELECT user_id, created_at
    FROM auth.sessions
    WHERE created_at >= :from
)
SELECT u.id, rs.created_at
FROM auth.users u
JOIN recent_sessions rs ON rs.user_id = u.id
WHERE u.deleted_at IS NULL;
```

**좋은 이유:**

- CTE로 query 단계를 읽기 쉽게 분해했다
- predicate pushdown 이점이 중요한 경우 NOT MATERIALIZED 의도를 드러낸다
- CTE를 성능 힌트처럼 무의식적으로 쓰지 않는다

PostgreSQL은 side-effect-free CTE의 folding/materialization 규칙과 NOT MATERIALIZED의 trade-off를 설명한다.

## 나쁜 예시

### 예시 1. LIMIT만 두고 정렬을 생략한다

```sql
SELECT id, user_id, created_at
FROM auth.sessions
WHERE user_id = :userId
LIMIT 20;
```

**나쁜 이유:**

- 어떤 20건인지 정의되지 않는다
- 실행 계획과 실행 시점에 따라 다른 부분집합이 나올 수 있다
- API와 배치 결과의 재현성이 깨진다

PostgreSQL은 LIMIT을 ORDER BY 없이 쓰면 예측 불가능한 subset을 얻는다고 설명한다.

### 예시 2. LEFT JOIN인데 오른쪽 조건을 WHERE에 내려 의미를 바꾼다

```sql
SELECT u.id, u.email, s.id AS active_session_id
FROM auth.users u
LEFT JOIN auth.sessions s
  ON s.user_id = u.id
WHERE s.revoked_at IS NULL;
```

**나쁜 이유:**

- session이 없는 사용자도 남겨야 하는데 결과에서 사라질 수 있다
- 사실상 inner join처럼 동작할 수 있다
- outer join의 핵심 의미를 망가뜨린다

PostgreSQL은 outer join에서 ON과 WHERE가 동등하지 않다고 설명한다.

### 예시 3. NATURAL JOIN을 사용한다

```sql
SELECT *
FROM auth.users
NATURAL JOIN auth.user_profiles;
```

**나쁜 이유:**

- 동일 이름 컬럼이 추가되면 join 의미가 바뀔 수 있다
- 스키마 변경에 매우 취약하다
- 장기 유지보수 신뢰도가 낮다

PostgreSQL은 NATURAL이 USING보다 훨씬 위험하다고 명시한다.

### 예시 4. 존재 여부 확인을 COUNT(*)로 처리한다

```sql
SELECT CASE
         WHEN count(*) > 0 THEN true
         ELSE false
       END
FROM auth.user_roles
WHERE user_id = :userId
  AND role_name = 'ADMIN';
```

**나쁜 이유:**

- 존재 여부만 필요해도 집계를 수행한다
- COUNT(*)는 공짜가 아니며, 전체 집계는 테이블 크기에 비례하는 비용이 들 수 있다
- EXISTS가 더 직접적인 표현이다

PostgreSQL은 전체 count(*)가 테이블 또는 전체 인덱스를 스캔해야 할 수 있다고 설명한다.

### 예시 5. NOT IN에 null 가능성을 남겨 둔다

```sql
SELECT u.id, u.email
FROM auth.users u
WHERE u.id NOT IN (
    SELECT s.user_id
    FROM auth.sessions s
    WHERE s.revoked_at IS NULL
);
```

**나쁜 이유:**

- subquery 결과에 null이 섞이면 NOT IN 결과가 true가 아니라 null이 될 수 있다
- null semantics를 이해하지 못하면 버그를 만들기 쉽다
- 이런 anti-join은 보통 NOT EXISTS가 더 안전하다

PostgreSQL은 NOT IN의 null semantics를 명시적으로 경고한다.

### 예시 6. row filter를 HAVING으로 올린다

```sql
SELECT user_id, count(*)
FROM auth.sessions
GROUP BY user_id
HAVING max(created_at) >= :from
   AND user_id = :userId;
```

**나쁜 이유:**

- user_id = :userId는 grouping 전에 걸러도 되는 row filter다
- 불필요하게 더 많은 행을 grouping하게 만든다
- WHERE와 HAVING의 역할이 섞여 있다

PostgreSQL 튜토리얼은 aggregate가 필요 없는 제한은 WHERE가 더 효율적이라고 설명한다.

### 예시 7. 잘못된 join을 DISTINCT로 덮는다

```sql
SELECT DISTINCT u.id, u.email
FROM auth.users u
JOIN auth.user_roles ur ON ur.user_id = u.id
WHERE ur.role_name IN ('ADMIN', 'MANAGER');
```

**나쁜 이유:**

- row multiplication 원인을 해결하지 않고 중복만 제거한다
- 결과 의미가 DISTINCT에 의존하게 된다
- 존재 여부가 목적이라면 EXISTS가 더 직접적이다

PostgreSQL에서 DISTINCT는 실제로 duplicate row를 제거하는 의미 연산이다. 따라서 이 패턴은 보통 구조 문제를 가리는 나쁜 신호다.

### 예시 8. DISTINCT ON을 ORDER BY 없이 사용한다

```sql
SELECT DISTINCT ON (user_id)
    user_id, id, created_at
FROM auth.sessions;
```

**나쁜 이유:**

- 사용자별 어떤 세션이 남는지 예측할 수 없다
- first row 선택 기준이 정의되지 않는다
- 운영 결과가 비결정적이다

PostgreSQL은 DISTINCT ON의 first row는 ORDER BY가 없으면 예측 불가능하다고 설명한다.
