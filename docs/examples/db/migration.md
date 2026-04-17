# Migration 예시

## 좋은 예시

### 예시 1. 새 nullable 컬럼 추가 → backfill → default 설정으로 나눈다

```sql
ALTER TABLE auth.users
ADD COLUMN status text;

-- 배치/스크립트로 backfill 수행
-- UPDATE auth.users SET status = 'ACTIVE' WHERE status IS NULL;

ALTER TABLE auth.users
ALTER COLUMN status SET DEFAULT 'ACTIVE';
```

**좋은 이유:**

- 컬럼 추가를 빠른 additive change로 분리했다
- 과거 데이터 보정과 future default를 분리했다
- `SET DEFAULT`가 기존 row를 바꾸지 않는다는 점을 전제로 설계했다

PostgreSQL은 컬럼 추가가 가능하고, default 변경은 이후 insert/update에만 영향을 주며 기존 row를 바꾸지 않는다고 설명한다.

### 예시 2. 큰 테이블의 CHECK/FK는 NOT VALID 후 나중에 검증한다

```sql
ALTER TABLE ordering.orders
ADD CONSTRAINT ck_orders__amount_positive
CHECK (amount > 0) NOT VALID;

ALTER TABLE ordering.orders
VALIDATE CONSTRAINT ck_orders__amount_positive;
```

**좋은 이유:**

- 제약 추가 시점의 긴 full scan 영향을 줄인다
- 새로 들어오는/갱신되는 row에는 바로 제약이 적용된다
- 기존 데이터 검증은 별도 단계로 분리한다

PostgreSQL은 `NOT VALID` 제약이 기존 row scan을 건너뛰고, 이후 `VALIDATE CONSTRAINT`로 검증할 수 있으며 validation은 `SHARE UPDATE EXCLUSIVE` lock으로 수행된다고 설명한다.

### 예시 3. NOT NULL은 backfill과 증명 단계를 거쳐 올린다

```sql
ALTER TABLE auth.users
ADD CONSTRAINT ck_users__status_not_null
CHECK (status IS NOT NULL) NOT VALID;

-- backfill 수행
-- UPDATE auth.users SET status = 'ACTIVE' WHERE status IS NULL;

ALTER TABLE auth.users
VALIDATE CONSTRAINT ck_users__status_not_null;

ALTER TABLE auth.users
ALTER COLUMN status SET NOT NULL;

ALTER TABLE auth.users
DROP CONSTRAINT ck_users__status_not_null;
```

**좋은 이유:**

- null 방지 강화를 한 번에 몰아넣지 않았다
- PostgreSQL이 valid CHECK로 null 불가능함을 증명하면 `SET NOT NULL` scan을 건너뛸 수 있는 점과 잘 맞는다
- 운영 중 영향도를 줄이기 좋은 패턴이다

PostgreSQL은 `SET NOT NULL`이 보통 table scan을 하지만, valid CHECK가 null 불가능함을 증명하면 scan을 생략할 수 있다고 설명한다.

### 예시 4. 운영 인덱스는 CONCURRENTLY로 만들고, 필요하면 제약으로 승격한다

```sql
CREATE UNIQUE INDEX CONCURRENTLY uq_users__email_idx
ON auth.users (email);

ALTER TABLE auth.users
ADD CONSTRAINT uq_users__email
UNIQUE USING INDEX uq_users__email_idx;
```

**좋은 이유:**

- 인덱스 build 중 write block을 줄인다
- 기존 인덱스를 활용해 빠르게 UNIQUE 제약으로 승격한다
- 큰 테이블의 unique 추가에 적합한 공식 경로다

PostgreSQL은 `CREATE INDEX CONCURRENTLY`가 production environment에 유용하고, 기존 unique index를 UNIQUE/PRIMARY KEY 제약으로 전환할 수 있다고 설명한다.

### 예시 5. PostgreSQL 11+에서는 상수 default 컬럼 추가를 안전한 후보로 본다

```sql
ALTER TABLE auth.sessions
ADD COLUMN source text DEFAULT 'LOCAL';
```

**좋은 이유:**

- 상수 default라면 PostgreSQL 11+에서 빠른 metadata 기반 처리 경로에 들어갈 수 있다
- 운영 중 큰 rewrite를 피할 가능성이 높다
- 상수 default라는 점이 분명하다

PostgreSQL 11 release notes와 현재 ALTER TABLE 문서는 non-volatile/default constant 컬럼 추가가 table rewrite를 피할 수 있다고 설명한다.

## 나쁜 예시

### 예시 1. 큰 테이블에 volatile default를 바로 추가한다

```sql
ALTER TABLE audit.audit_logs
ADD COLUMN created_bucket timestamp with time zone DEFAULT clock_timestamp();
```

**나쁜 이유:**

- `clock_timestamp()`는 volatile default다
- PostgreSQL은 이런 경우 전체 테이블과 인덱스 rewrite가 일어날 수 있다고 설명한다
- 운영 테이블에서는 매우 위험할 수 있다

공식 문서는 volatile default 컬럼 추가가 rewrite를 유발한다고 설명한다.

### 예시 2. 운영 인덱스를 일반 CREATE INDEX로 바로 만든다

```sql
CREATE INDEX ix_orders__created_at
ON ordering.orders (created_at);
```

**나쁜 이유:**

- write를 막을 수 있는 일반 index build를 사용한다
- 운영 중 대형 테이블에는 영향도가 과도할 수 있다
- 이런 경우는 `CREATE INDEX CONCURRENTLY`를 먼저 검토해야 한다

PostgreSQL은 `CREATE INDEX CONCURRENTLY`가 write를 막지 않고 production environment에 유용하다고 설명한다. 그 반대 의미로, 일반 build는 운영 중 더 보수적으로 다뤄야 한다.

### 예시 3. CREATE INDEX CONCURRENTLY를 트랜잭션 블록 안에 넣는다

```sql
BEGIN;

CREATE INDEX CONCURRENTLY ix_users__email
ON auth.users (email);

COMMIT;
```

**나쁜 이유:**

- PostgreSQL은 `CREATE INDEX CONCURRENTLY`가 transaction block 안에서 실행될 수 없다고 명시한다
- migration 도구가 이 구분을 지원하지 않으면 배포 시 실패한다
- non-transactional step으로 분리해야 한다

공식 문서는 regular `CREATE INDEX`는 transaction block 안에서 가능하지만, `CREATE INDEX CONCURRENTLY`는 불가능하다고 설명한다.

### 예시 4. backfill 없이 바로 NOT NULL을 건다

```sql
ALTER TABLE auth.users
ADD COLUMN status text;

ALTER TABLE auth.users
ALTER COLUMN status SET NOT NULL;
```

**나쁜 이유:**

- 기존 row가 null일 가능성을 무시한다
- `SET NOT NULL`은 보통 전체 테이블 scan을 요구한다
- 데이터 보정 없이 곧바로 강한 제약을 올리는 구조다

PostgreSQL은 `SET NOT NULL`이 기존 row에 null이 없어야 하고, 보통 이를 확인하기 위해 전체 테이블을 스캔한다고 설명한다.

### 예시 5. 타입 변경을 무심코 직접 수행한다

```sql
ALTER TABLE billing.payments
ALTER COLUMN amount TYPE numeric(19,4);
```

**나쁜 이유:**

- 타입 변경은 기본적으로 rewrite/rebuild 후보다
- 큰 테이블에서는 시간과 디스크 사용량이 매우 커질 수 있다
- 더 안전한 staged migration이 필요한지 먼저 검토해야 한다

PostgreSQL은 타입 변경이 보통 테이블과 인덱스를 rewrite/rebuild하고, 상당한 시간과 최대 거의 두 배 디스크를 일시적으로 요구할 수 있다고 설명한다.

### 예시 6. 앱 호환성 검증 전에 rename/drop부터 수행한다

```sql
ALTER TABLE auth.users
RENAME COLUMN email TO login_id;

ALTER TABLE auth.users
DROP COLUMN username;
```

**나쁜 이유:**

- 기술적으로는 가능한 DDL이지만, 애플리케이션/쿼리/배치/운영 스크립트와의 호환성을 즉시 깨뜨릴 수 있다
- backward-compatible rollout 경로가 없다
- 이런 변경은 마지막 cleanup 단계에서만 허용하는 것이 안전하다

PostgreSQL은 rename/drop을 지원하지만, 프로젝트 운영 기준에서는 destructive/비호환 변경으로 본다. 특히 `DROP COLUMN`은 빠르지만 디스크 공간도 즉시 줄지 않는다.

### 예시 7. failed concurrent build 뒤 INVALID 인덱스를 방치한다

```sql
-- 실패한 CREATE INDEX CONCURRENTLY 이후
-- 아무 조치 없이 배포 종료
```

**나쁜 이유:**

- PostgreSQL은 실패한 concurrent build가 INVALID 인덱스를 남길 수 있다고 설명한다
- 이 인덱스는 query에는 안 쓰여도 update overhead는 계속 발생한다
- drop 후 재시도나 `REINDEX INDEX CONCURRENTLY` 같은 정리 절차가 필요하다

공식 문서는 invalid index가 남을 수 있고, 권장 복구 방법은 drop 후 다시 시도하거나 concurrent reindex라고 설명한다.
