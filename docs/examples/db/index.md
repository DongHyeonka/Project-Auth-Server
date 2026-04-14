# Index 예시

## 좋은 예시

### 예시 1. 자주 조회되는 FK에 단일 컬럼 B-tree 인덱스를 둔다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE TABLE auth.sessions (
    id bigint GENERATED ALWAYS AS IDENTITY,
    user_id bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_sessions PRIMARY KEY (id),
    CONSTRAINT fk_sessions__users FOREIGN KEY (user_id)
        REFERENCES auth.users (id)
        ON DELETE CASCADE
);

CREATE INDEX ix_sessions__user_id ON auth.sessions (user_id);
```

**좋은 이유:**

- FK는 참조 무결성을 보장하지만, 참조하는 쪽 인덱스는 자동 생성되지 않는다
- 세션을 사용자 기준으로 자주 조회하거나, 사용자 삭제 시 자식 세션을 찾는 경로에 도움이 된다
- 단순하고 신뢰도 높은 기본 패턴이다

이 예시는 PostgreSQL의 FK 동작과 B-tree 기본 사용 패턴에 맞는 안전한 best practice다.

### 예시 2. 복합 검색 패턴에는 왼쪽 컬럼 순서를 고려한 멀티 컬럼 인덱스를 둔다

```sql
CREATE TABLE ordering.orders (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE INDEX ix_orders__tenant_status_created_at
    ON ordering.orders (tenant_id, status, created_at DESC);
```

**좋은 이유:**

- tenant_id = ? AND status = ? AND created_at < ? ORDER BY created_at DESC 같은 패턴에 잘 맞는다
- equality 필터를 왼쪽에 두고, range/정렬 컬럼을 뒤에 둔 전형적인 B-tree 설계다
- 멀티 컬럼 인덱스가 실제 쿼리 패턴에 직접 대응한다

PostgreSQL은 B-tree 멀티 컬럼 인덱스가 leading columns 제약에 가장 효율적이라고 설명한다.

### 예시 3. soft delete 활성 행만 자주 조회되면 partial index를 사용한다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL,
    email text NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE INDEX ix_users__tenant_id_email__active
    ON auth.users (tenant_id, email)
    WHERE deleted_at IS NULL;
```

**좋은 이유:**

- 전체 사용자보다 “삭제되지 않은 사용자”만 자주 조회되는 경우에 맞는 설계다
- hot subset만 인덱싱하므로 전체 인덱스보다 더 작고 유지 비용도 낮을 수 있다
- predicate가 단순하고 쿼리와 맞추기 쉽다

PostgreSQL 공식 문서도 partial index를 이런 subset 최적화에 쓰도록 설명한다.

### 예시 4. 대소문자 무시 검색은 expression index로 맞춘다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY,
    email text NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_users__lower_email
    ON auth.users (lower(email));
```

**좋은 이유:**

- 검색과 고유성 규칙이 모두 lower(email) 의미에 맞춰져 있다
- 애플리케이션에서 임시 변환만 하는 것보다 DB 규칙이 더 명확하다
- 표현식 인덱스의 대표적인 안전 사용 사례다

PostgreSQL은 lower(col) 같은 expression index를 공식 지원하고, unique expression index로 단순 unique constraint로 표현하기 어려운 규칙도 강제할 수 있다고 설명한다.

### 예시 5. 목록 조회 최적화가 확실하면 INCLUDE를 보수적으로 사용한다

```sql
CREATE TABLE ordering.orders (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    total_amount numeric(19,4) NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE INDEX ix_orders__tenant_status_created_at
    ON ordering.orders (tenant_id, status, created_at DESC)
    INCLUDE (total_amount);
```

**좋은 이유:**

- 자주 실행되는 목록 조회가 tenant_id, status, created_at 기준으로 필터/정렬되고, 응답에는 total_amount가 필요할 때 유효하다
- total_amount는 non-key payload라 uniqueness/탐색 키 의미를 어지럽히지 않는다
- 작은 payload 컬럼만 추가한 보수적 covering index다

PostgreSQL은 INCLUDE가 index-only scan을 돕지만, 인덱스 크기를 키우므로 보수적으로 사용해야 한다고 설명한다.

### 예시 6. jsonb 전체 containment 검색은 GIN을 검토한다

```sql
CREATE TABLE integration.webhook_events (
    id bigint GENERATED ALWAYS AS IDENTITY,
    payload jsonb NOT NULL,
    received_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_webhook_events PRIMARY KEY (id)
);

CREATE INDEX ix_webhook_events__payload_gin
    ON integration.webhook_events
    USING GIN (payload);
```

**좋은 이유:**

- payload @> ..., key existence, jsonpath 검색 같은 jsonb 검색 패턴에 맞는다
- B-tree로 해결할 수 없는 composite value 내부 검색을 GIN으로 처리한다
- jsonb를 유지해야 하는 상황에서 가장 전형적인 공식 패턴이다

PostgreSQL은 jsonb 검색에 GIN을 사용할 수 있고, key/key-value search 및 containment에 적합하다고 설명한다.

### 예시 7. 대형 append-only 로그는 BRIN을 검토한다

```sql
CREATE TABLE audit.audit_logs (
    id bigint GENERATED ALWAYS AS IDENTITY,
    occurred_at timestamp with time zone NOT NULL,
    actor_id bigint,
    action text NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

CREATE INDEX ix_audit_logs__occurred_at_brin
    ON audit.audit_logs
    USING BRIN (occurred_at);
```

**좋은 이유:**

- 아주 큰 로그 테이블에서 occurred_at이 물리 저장 순서와 자연스럽게 상관될 가능성이 높다
- BRIN은 작은 인덱스로 큰 범위를 건너뛸 수 있다
- append-only 성격이 강한 로그/이력성 테이블과 잘 맞는다

PostgreSQL은 BRIN이 매우 큰 테이블과 물리 순서 상관성이 있는 컬럼에 적합하다고 설명한다.

## 나쁜 예시

### 예시 1. PK가 이미 만든 인덱스를 다시 만든다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY,
    email text NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE INDEX ix_users__id ON auth.users (id);
```

**나쁜 이유:**

- PK가 이미 unique B-tree 인덱스를 자동 생성한다
- 같은 의미의 중복 인덱스라 쓰기 비용과 저장 비용만 늘린다
- 운영상 관리 포인트만 증가한다

PostgreSQL 공식 문서는 PK/UNIQUE 제약이 자동으로 인덱스를 만든다고 설명한다.

### 예시 2. 실제 쿼리와 무관하게 긴 멀티 컬럼 인덱스를 만든다

```sql
CREATE INDEX ix_orders__tenant_status_type_created_at_updated_at
    ON ordering.orders (tenant_id, status, type, created_at, updated_at);
```

**나쁜 이유:**

- 어떤 쿼리를 위한 인덱스인지 설명하기 어렵다
- 멀티 컬럼이 길어질수록 유지 비용이 커지고 활용 범위도 애매해진다
- 대부분의 경우 단일/짧은 복합 인덱스 조합이 더 낫다

PostgreSQL도 멀티 컬럼 인덱스는 신중히 사용해야 하고, 3개를 넘는 경우는 드물게만 유효하다고 설명한다.

### 예시 3. 단일 컬럼 DESC 인덱스를 습관적으로 만든다

```sql
CREATE INDEX ix_orders__created_at_desc
    ON ordering.orders (created_at DESC);
```

**나쁜 이유:**

- 단일 컬럼 B-tree는 backward scan이 가능하므로 대개 별도 DESC 인덱스 이점이 없다
- mixed ordering이 아닌데도 특수 정렬을 도입해 관리 복잡도만 올린다
- 실제 쿼리 근거가 부족하다

PostgreSQL은 ordered scan을 forward/backward 모두 지원하고, 단일 컬럼에서는 별도 DESC 인덱스가 대체로 유용하지 않다고 설명한다.

### 예시 4. partial index predicate를 쿼리와 다르게 만든다

```sql
CREATE INDEX ix_users__active_recent
    ON auth.users (tenant_id, email)
    WHERE deleted_at IS NULL AND last_login_at > now() - interval '30 days';
```

**나쁜 이유:**

- now() 같은 시간 의존 조건은 안정적인 partial index predicate로 부적절하다
- 쿼리와 predicate가 정확히 맞지 않으면 planner가 인덱스를 잘 쓰지 못한다
- 데이터 분포와 시간 경계가 계속 바뀌므로 유지 신뢰도가 낮다

PostgreSQL은 partial index predicate가 planner가 인식 가능한 형태여야 한다고 설명한다.

### 예시 5. 큰 payload를 INCLUDE에 넣는다

```sql
CREATE INDEX ix_posts__author_created_at
    ON board.posts (author_id, created_at DESC)
    INCLUDE (content, metadata_json);
```

**나쁜 이유:**

- 큰 text/json payload는 인덱스를 크게 부풀린다
- 인덱스 tuple 크기 제한에 걸릴 수 있고, 검색 자체도 느려질 수 있다
- INCLUDE는 작은 응답용 컬럼에만 보수적으로 써야 한다

PostgreSQL은 wide non-key column을 INCLUDE에 넣는 것을 보수적으로 하라고 명시한다.

### 예시 6. 운영 중 대형 테이블에 일반 CREATE INDEX를 바로 실행한다

```sql
CREATE INDEX ix_events__occurred_at
    ON audit.audit_logs (occurred_at);
```

**나쁜 이유:**

- 일반 CREATE INDEX는 빌드 동안 writes를 막는다
- 운영 중 대형 테이블에서는 서비스 영향이 매우 클 수 있다
- 이런 경우는 CREATE INDEX CONCURRENTLY 여부를 먼저 검토해야 한다

PostgreSQL 공식 문서도 일반 index build는 writes를 block하고, 운영 환경에서는 종종 unacceptable하다고 설명한다.

### 예시 7. jsonb 키 검색에 B-tree를 건다

```sql
CREATE INDEX ix_webhook_events__payload
    ON integration.webhook_events (payload);
```

**나쁜 이유:**

- jsonb @>, ?, @?, @@ 같은 검색은 B-tree 기본 패턴과 맞지 않는다
- composite value 내부 검색은 GIN 같은 전용 접근 방식이 더 적합하다
- 타입/연산자 특성을 무시한 설계다

PostgreSQL은 jsonb key/key-value 검색에 GIN을 공식적으로 권장 가능한 접근 방식으로 설명한다.
