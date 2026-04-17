# PK / FK / UNIQUE / CHECK 예시

## 좋은 예시

### 예시 1. 대표 식별자는 PK, 비즈니스 고유성은 UNIQUE로 분리한다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY,
    email text NOT NULL,
    username text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users__email UNIQUE (email),
    CONSTRAINT uq_users__username UNIQUE (username)
);
```

**좋은 이유:**

- 대표 식별자 id를 PK로 둔다
- 이메일과 username은 business unique로 분리한다
- identity에만 기대지 않고 PK가 유일성을 보장한다

PostgreSQL은 PK가 대표 식별자이며 unique B-tree 인덱스를 자동 생성한다고 설명하고, identity는 자동 생성일 뿐 uniqueness를 보장하지 않는다고 명시한다.

### 예시 2. 필수 관계는 FK + NOT NULL, 삭제 정책은 관계 의미에 맞춘다

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

- 세션은 사용자에 종속된 구성요소이므로 ON DELETE CASCADE가 자연스럽다
- 필수 관계를 NOT NULL로 닫았다
- FK 컬럼 인덱스를 별도로 생성했다

PostgreSQL 공식 문서도 구성요소 관계에는 CASCADE가 적절할 수 있고, FK는 참조하는 쪽 인덱스를 자동 생성하지 않는다고 설명한다.

### 예시 3. soft delete 환경의 조건부 고유성은 partial unique index로 표현한다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY,
    tenant_id bigint NOT NULL,
    email text NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_users__tenant_email_active
    ON auth.users (tenant_id, email)
    WHERE deleted_at IS NULL;
```

**좋은 이유:**

- “삭제되지 않은 사용자만 tenant 내 email 유일”이라는 조건부 고유성을 정확히 표현한다
- 일반 UNIQUE constraint로는 일부 행에만 적용되는 uniqueness를 표현할 수 없다

PostgreSQL 공식 문서는 일부 행에만 적용되는 uniqueness restriction은 unique constraint가 아니라 unique partial index로 표현해야 한다고 설명한다.

### 예시 4. CHECK는 같은 행 안의 불변식에만 사용한다

```sql
CREATE TABLE billing.payments (
    id bigint GENERATED ALWAYS AS IDENTITY,
    amount numeric(19,4) NOT NULL,
    refunded_amount numeric(19,4) NOT NULL DEFAULT 0,
    status text NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments__amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments__refunded_amount_range
        CHECK (refunded_amount >= 0 AND refunded_amount <= amount),
    CONSTRAINT ck_payments__status
        CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'REFUNDED'))
);
```

**좋은 이유:**

- 모두 같은 행 안에서 평가 가능한 규칙이다
- null 금지는 NOT NULL로, 값 범위는 CHECK로 역할을 분리했다
- CHECK에 cross-table 의존이 없다

PostgreSQL은 CHECK가 row-local invariant에 적합하고, null 금지는 NOT NULL로 표현하는 것이 맞다고 설명한다.

### 예시 5. 조인 테이블은 복합 PK를 예외적으로 사용할 수 있다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE TABLE auth.roles (
    id bigint GENERATED ALWAYS AS IDENTITY,
    CONSTRAINT pk_roles PRIMARY KEY (id)
);

CREATE TABLE auth.user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    granted_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles__users FOREIGN KEY (user_id)
        REFERENCES auth.users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_roles__roles FOREIGN KEY (role_id)
        REFERENCES auth.roles (id)
        ON DELETE RESTRICT
);
```

**좋은 이유:**

- 조인 테이블에서는 (user_id, role_id) 조합 자체가 자연스러운 정체성이다
- 복합 PK가 FK와 겹쳐도 의미가 분명하다
- 삭제 정책도 관계 의미에 따라 다르게 선택했다

PostgreSQL은 복합 PK와 FK를 모두 지원하며, 문서 예시에서도 many-to-many 구조에서 이런 형태를 보여준다. 다만 JPA에서는 복합 PK가 별도 키 클래스를 요구하므로 예외적으로 사용하는 편이 안전하다.

## 나쁜 예시

### 예시 1. PK 없이 UNIQUE만으로 테이블을 운영한다

```sql
CREATE TABLE auth.users (
    email text NOT NULL UNIQUE,
    username text NOT NULL UNIQUE
);
```

**나쁜 이유:**

- 대표 식별자가 없다
- FK 기본 참조 대상과 ORM 식별 의미가 불명확하다
- “고유한 컬럼 몇 개”와 “대표 PK”의 역할이 섞인다

PostgreSQL도 모든 테이블에 PK를 강제하지는 않지만, 일반적으로 두는 것이 가장 좋다고 설명한다.

### 예시 2. nullable UNIQUE를 두고 NULL도 하나만 허용된다고 착각한다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_subject text UNIQUE
);
```

**나쁜 이유:**

- PostgreSQL 기본 동작에서는 NULL이 서로 다른 값으로 취급된다
- 따라서 external_subject가 NULL인 행이 여러 개 들어갈 수 있다
- “값이 없으면 하나만 허용” 의미라면 현재 설계는 틀렸다

이 경우는 NOT NULL, UNIQUE NULLS NOT DISTINCT, 또는 모델 재설계가 필요하다.

### 예시 3. 필수 관계인데 nullable FK로 열어 둔다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY
);

CREATE TABLE auth.sessions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint,
    CONSTRAINT fk_sessions__users FOREIGN KEY (user_id)
        REFERENCES auth.users (id)
);
```

**나쁜 이유:**

- 세션이 반드시 사용자에 속해야 한다면 user_id는 NOT NULL이어야 한다
- FK만으로는 NULL을 막지 못한다
- 필수 관계를 스키마가 보장하지 못한다

PostgreSQL은 참조 컬럼에 null이 있으면 FK를 회피할 수 있다고 설명한다.

### 예시 4. 독립 객체 관계에 무분별하게 CASCADE를 건다

```sql
CREATE TABLE catalog.products (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY
);

CREATE TABLE ordering.order_items (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint NOT NULL,
    CONSTRAINT fk_order_items__products FOREIGN KEY (product_id)
        REFERENCES catalog.products (id)
        ON DELETE CASCADE
);
```

**나쁜 이유:**

- product와 order item 관계는 도메인에 따라 독립 객체일 수 있다
- 상품 삭제가 주문 이력 일부를 연쇄 삭제하면 운영상 매우 위험할 수 있다
- 이 경우는 RESTRICT 또는 NO ACTION이 더 자연스러운 경우가 많다

PostgreSQL 공식 문서도 독립 객체 관계라면 RESTRICT 또는 NO ACTION이 더 적절하다고 설명한다.

### 예시 5. null 금지를 CHECK로 우회한다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text,
    CONSTRAINT ck_users__email_not_null CHECK (email IS NOT NULL)
);
```

**나쁜 이유:**

- null 금지는 NOT NULL이 더 직접적이고 효율적이다
- 역할이 다른 제약을 섞고 있다
- 팀 규칙 해석도 흐려진다

PostgreSQL 공식 문서도 CHECK (col IS NOT NULL)보다 explicit NOT NULL이 더 효율적이라고 설명한다.

### 예시 6. cross-row 규칙을 CHECK로 해결하려 한다

```sql
CREATE TABLE auth.user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    is_primary boolean NOT NULL,
    CONSTRAINT ck_user_roles__only_one_primary
        CHECK (
            NOT is_primary
            OR role_id IS NOT NULL
        )
);
```

**나쁜 이유:**

- “사용자당 primary role은 하나만” 같은 규칙은 이런 CHECK로 보장되지 않는다
- CHECK는 다른 행을 기준으로 유일성을 유지하는 수단이 아니다
- 이런 요구는 (user_id) 조건부 unique index 같은 방식으로 풀어야 한다

PostgreSQL은 cross-row / cross-table 규칙에 CHECK를 쓰지 말고 UNIQUE, FK, EXCLUDE, trigger를 검토하라고 안내한다.
