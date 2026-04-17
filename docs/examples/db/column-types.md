# Column Types 예시

## 좋은 예시

### 예시 1. 문자열 기본값은 text, 실제 길이 규칙은 varchar(n)

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email varchar(320) NOT NULL,
    display_name text NOT NULL,
    bio text,
    created_at timestamp with time zone NOT NULL
);
```

**좋은 이유:**

- 이메일은 실제 길이 제한을 반영했다
- 자유 텍스트는 text
- 시점은 timestamptz
- 생성 키는 identity를 사용한다

### 예시 2. 금액은 numeric, 공개 식별자는 uuid

```sql
CREATE TABLE billing.payments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency varchar(3) NOT NULL,
    paid_at timestamp with time zone
);
```

**좋은 이유:**

- 정밀도가 필요한 금액을 numeric 으로 저장한다
- UUID를 문자열로 저장하지 않는다
- 시간은 시점 의미로 저장한다

### 예시 3. 외부 payload 보관은 jsonb

```sql
CREATE TABLE integration.webhook_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider varchar(50) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamp with time zone NOT NULL
);
```

**좋은 이유:**

- 반정형 외부 payload 저장 용도에 맞다
- 나중에 검색/인덱싱 여지도 있다
- 원문 텍스트 보존이 핵심이 아니라면 jsonb 가 더 실용적이다

## 나쁜 예시

### 예시 1. 모든 문자열을 varchar(255) 로 통일한다

```sql
CREATE TABLE auth.users (
    email varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    bio varchar(255)
);
```

**나쁜 이유:**

- 실제 길이 규칙을 표현하지 못한다
- 자유 텍스트까지 임의 길이로 잘라 버린다
- PostgreSQL은 text 와 varchar 사이에 일반 성능 이점이 없다고 설명한다

### 예시 2. 금액을 부동소수점으로 저장한다

```sql
CREATE TABLE billing.payments (
    amount double precision NOT NULL
);
```

**나쁜 이유:**

- double precision 은 부정확한 floating-point 타입이다
- 금액/정산에 부적절하다

### 예시 3. UUID를 문자열에 저장한다

```sql
CREATE TABLE auth.sessions (
    session_id varchar(36) NOT NULL
);
```

**나쁜 이유:**

- PostgreSQL이 네이티브 uuid 타입을 제공하는데 활용하지 않는다
- 문자열 유효성/연산/저장 의미가 흐려진다

### 예시 4. 시점을 로컬 datetime처럼 저장한다

```sql
CREATE TABLE auth.audit_logs (
    created_at timestamp NOT NULL
);
```

**나쁜 이유:**

- 절대 시점인지 로컬 시각인지 의미가 불명확하다
- 일반적인 생성/수정 시각은 timestamp with time zone 이 더 안전하다
