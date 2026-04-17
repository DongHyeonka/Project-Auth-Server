# Audit Columns 예시

## 좋은 예시

### 예시 1. 최소 감사 컬럼은 created_at, updated_at을 공통으로 둔다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT current_timestamp,
    updated_at timestamp with time zone NOT NULL DEFAULT current_timestamp
);
```

**좋은 이유:**

- 생성/수정 시각을 공통 규칙으로 표준화한다
- 두 컬럼 모두 절대 시점 타입을 사용한다
- insert 시점 기본값을 DB가 일관되게 채운다

PostgreSQL은 `CURRENT_TIMESTAMP`를 timestamp column default의 대표 예시로 설명하고, default expression은 row 삽입 시 평가된다고 설명한다. `CURRENT_TIMESTAMP`/`now()`는 transaction start time 의미를 갖는다.

### 예시 2. updated_at은 DB trigger 하나만 source of truth로 둔다

```sql
CREATE OR REPLACE FUNCTION common_set_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at := current_timestamp;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON auth.users
FOR EACH ROW
WHEN (OLD.* IS DISTINCT FROM NEW.*)
EXECUTE FUNCTION common_set_updated_at();
```

**좋은 이유:**

- `updated_at` 갱신 책임이 DB에 명확하게 모인다
- BEFORE ROW trigger가 NEW를 수정해 반환하는 PostgreSQL 공식 모델과 맞다
- `WHEN (OLD.* IS DISTINCT FROM NEW.*)`로 실제 변경이 있을 때만 갱신하게 만들 수 있다

PostgreSQL은 BEFORE row trigger가 NEW를 수정하고 반환할 수 있다고 설명하고, `WHEN (OLD.* IS DISTINCT FROM NEW.*)` 예시도 공식 문서에 제공한다.

### 예시 3. 주체 컬럼은 애플리케이션 principal 식별자로 둔다

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users", schema = "auth")
public class User {

    @Id
    private Long id;

    @CreatedBy
    @Column(name = "created_by")
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
```

**좋은 이유:**

- `created_by`, `updated_by`를 DB 연결 계정이 아니라 애플리케이션 actor 식별자로 다룬다
- Spring Data JPA의 표준 auditing metadata를 사용한다
- 시간 컬럼과 주체 컬럼의 source를 분리할 수 있다

Spring Data JPA는 `@CreatedBy`, `@LastModifiedBy`를 제공하고, `AuditorAware<T>`가 현재 애플리케이션 principal을 제공하도록 설명한다.

### 예시 4. 시간과 주체를 필요한 만큼만 선택적으로 둔다

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "login_failures", schema = "auth")
public class LoginFailure {

    @Id
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

**좋은 이유:**

- 시간 정보만 필요하면 날짜 감사 컬럼만 써도 된다
- Spring Data JPA는 시간 추적만 하는 경우 `AuditorAware`가 필수가 아니라고 설명한다
- 모든 테이블에 주체 컬럼을 기계적으로 강제하지 않는다

Spring Data JPA는 생성/수정 날짜만 추적하는 경우 `AuditorAware`가 필요 없다고 설명한다.

### 예시 5. Hibernate timestamp를 쓴다면 source를 명시적으로 선택한다

```java
@Entity
@Table(name = "sessions", schema = "auth")
public class Session {

    @Id
    private Long id;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
```

**좋은 이유:**

- Hibernate의 기본 VM source 대신 DB source를 명시적으로 선택한다
- 다중 인스턴스 환경에서 시간 source를 DB로 맞추고 싶다는 의도가 드러난다
- 같은 컬럼을 또 다른 trigger가 동시에 갱신하지만 않는다면 일관성이 높다

Hibernate는 `@CreationTimestamp`, `@UpdateTimestamp`가 기본적으로 VM(in memory)에서 생성되지만, `source()`로 변경할 수 있고 `SourceType.DB`는 DB가 값을 생성함을 뜻한다고 설명한다.

## 나쁜 예시

### 예시 1. 감사 컬럼 없이 테이블마다 임의 이름을 쓴다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL,
    reg_dt timestamp,
    mod_ymd text
);
```

**나쁜 이유:**

- 공통 규칙이 없어 해석과 조회가 어려워진다
- 수정 시각을 문자열로 저장해 타입 의미가 무너진다
- 생성/수정 메타데이터가 표준화되지 않는다

PostgreSQL은 timestamp/date/time 타입을 제공하고, `CURRENT_TIMESTAMP` 같은 기본 시간 함수도 제공한다. 감사 컬럼은 이런 표준 타입 위에서 일관되게 두는 편이 안전하다.

### 예시 2. default에 TIMESTAMP 'now' literal을 쓴다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at timestamp with time zone NOT NULL DEFAULT TIMESTAMP 'now'
);
```

**나쁜 이유:**

- PostgreSQL이 default 절에서는 이 형태를 쓰지 말라고 명시한다
- row 삽입 시점이 아니라 table creation 시점으로 고정될 수 있다
- `CURRENT_TIMESTAMP` 또는 `now()` 같은 함수형 표현이 맞다

PostgreSQL은 later evaluation이 필요한 DEFAULT 절에서 `TIMESTAMP 'now'`를 사용하지 말라고 설명한다.

### 예시 3. 애플리케이션 사용자 식별자를 current_user에 맡긴다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_by text NOT NULL DEFAULT current_user,
    updated_by text NOT NULL DEFAULT current_user
);
```

**나쁜 이유:**

- `current_user`는 DB 권한 검사에 쓰이는 사용자다
- connection pool, `SET ROLE`, `SECURITY DEFINER` 환경에서는 최종 애플리케이션 사용자와 다를 수 있다
- 일반 웹 애플리케이션의 actor 추적 컬럼으로는 부적절하다

PostgreSQL은 `current_user`가 권한 검사에 쓰이는 사용자이고, `SET ROLE`이나 `SECURITY DEFINER`에 의해 바뀔 수 있다고 설명한다. Spring Data JPA는 이런 경우 현재 principal을 `AuditorAware<T>`로 제공하도록 설계한다.

### 예시 4. 같은 updated_at을 DB trigger와 ORM이 동시에 관리한다

```java
@Entity
@Table(name = "users", schema = "auth")
public class User {

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
```

```sql
CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON auth.users
FOR EACH ROW
EXECUTE FUNCTION common_set_updated_at();
```

**나쁜 이유:**

- 같은 컬럼에 두 개의 writer가 생긴다
- VM source와 DB source가 섞일 수 있다
- 어떤 값이 권위 있는지 흐려지고 디버깅이 어려워진다

Hibernate는 `@UpdateTimestamp`의 기본 source가 VM이라고 설명하고, PostgreSQL trigger는 NEW row를 수정해 저장값을 바꿀 수 있다고 설명한다. 둘을 같은 컬럼에 동시에 쓰면 source of truth가 모호해진다.

### 예시 5. created_at을 business code가 직접 덮어쓴다

```java
user.setCreatedAt(OffsetDateTime.now());
user.setUpdatedAt(OffsetDateTime.now());
```

**나쁜 이유:**

- 생성 감사 컬럼이 도메인 로직에서 임의로 바뀔 수 있다
- DB default나 프레임워크 auditing 의미를 깨뜨린다
- 생성 메타데이터의 불변성이 사라진다

Hibernate는 `@CreationTimestamp` 필드는 애플리케이션이 직접 설정할 수 없다고 설명한다. 프로젝트도 같은 철학으로, 생성 감사 컬럼을 business code가 임의로 다루지 않게 한다.

### 예시 6. deleted_at과 version을 기본 감사 컬럼처럼 섞어 둔다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    version bigint NOT NULL
);
```

**나쁜 이유:**

- `deleted_at`은 soft delete 정책의 일부이고, `version`은 optimistic locking 메타데이터다
- 현재 row 감사 컬럼과 다른 목적의 컬럼을 한 범주로 섞는다
- 문서 경계와 코드 책임이 흐려진다

현재 문서 체계에서도 soft delete와 concurrency/version은 별도 주제로 분리되어 있다.
