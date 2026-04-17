# Soft Delete 예시

## 좋은 예시

### 예시 1. 기본 soft delete 컬럼은 deleted_at으로 둔다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL,
    display_name text NOT NULL,
    deleted_at timestamp with time zone
);
```

**좋은 이유:**

- soft delete를 삭제 시점으로 표현한다
- active row 조건이 `deleted_at IS NULL`로 단순해진다
- boolean-only보다 운영 추적성이 좋다

Hibernate는 TIMESTAMP soft delete 전략이 삭제된 시점을 추적한다고 설명한다. 프로젝트 기본값을 `deleted_at`으로 두는 것은 그 전략과 잘 맞는다.

### 예시 2. active row uniqueness는 partial unique index로 강제한다

```sql
CREATE UNIQUE INDEX uq_users__email__active
    ON auth.users (email)
    WHERE deleted_at IS NULL;
```

**좋은 이유:**

- active row 사이에서만 이메일 중복을 막는다
- soft-deleted row는 uniqueness 대상에서 제외할 수 있다
- PostgreSQL이 공식적으로 지원하는 부분집합 uniqueness 패턴이다

PostgreSQL은 일부 row에만 적용되는 uniqueness restriction은 unique constraint가 아니라 unique partial index로 표현해야 한다고 설명한다.

### 예시 3. soft delete는 DELETE가 아니라 UPDATE로 수행한다

```sql
UPDATE auth.users
SET deleted_at = current_timestamp
WHERE id = :userId
  AND deleted_at IS NULL;
```

**좋은 이유:**

- soft delete를 상태 전이로 표현한다
- 이미 삭제된 row에 대한 중복 처리도 막기 쉽다
- 일반 삭제와 물리 삭제를 분리하기 좋다

Hibernate는 soft delete를 실제 삭제 대신 indicator column update로 설명한다.

### 예시 4. Hibernate를 쓴다면 TIMESTAMP soft delete를 공식 기능으로 쓸 수 있다

```java
@Entity
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Table(name = "users", schema = "auth")
public class User {
    @Id
    private Long id;
}
```

**좋은 이유:**

- Hibernate의 공식 soft delete 기능을 사용한다
- TIMESTAMP 전략이 삭제 시점을 추적한다
- 컬럼 이름을 프로젝트 표준인 `deleted_at`으로 맞출 수 있다

Hibernate는 `@SoftDelete`가 TIMESTAMP 전략을 지원하고, indicator column 이름은 `columnName`으로 정의할 수 있다고 설명한다.

### 예시 5. @ManyToMany / @ElementCollection join table에는 soft delete를 제한적으로 적용할 수 있다

```java
@ManyToMany
@JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
)
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
private Set<Role> roles;
```

**좋은 이유:**

- Hibernate 공식 지원 범위 안에서 join table row를 soft delete할 수 있다
- 관계 row의 논리 삭제가 필요한 경우에만 제한적으로 쓸 수 있다
- 엔티티 자체 soft delete와 컬렉션 테이블 soft delete를 구분한다

Hibernate는 `@SoftDelete`를 `@ElementCollection`과 `@ManyToMany` collection table에 적용할 수 있다고 설명한다.

### 예시 6. soft delete와 bulk purge를 분리한다

```java
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("""
        delete from User u
        where u.deletedAt < :cutoff
    """)
    int purgeDeletedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
```

**좋은 이유:**

- 일반 삭제는 soft delete 경로로 두고, 오래된 삭제 row 정리는 purge 전용 경로로 분리한다
- bulk delete가 direct database delete임을 명시적으로 드러낸다
- soft delete와 physical purge를 같은 경로로 섞지 않는다

Spring Data JPA는 JPQL/Criteria bulk delete가 DB 직접 delete로 매핑되고 persistence context를 동기화하지 않는다고 설명한다. 이런 방식은 purge 전용 경로에서만 명시적으로 쓰는 편이 안전하다.

## 나쁜 예시

### 예시 1. soft delete 컬럼은 있지만 기본 조회에서 빼지 않는다

```sql
SELECT id, email
FROM auth.users
ORDER BY id DESC;
```

**나쁜 이유:**

- 삭제 row가 일반 조회에 섞인다
- soft delete가 조회 계약으로 완성되지 않는다
- active-row partial index와도 잘 맞지 않는다

PostgreSQL은 partial index가 query WHERE 조건이 predicate를 함의할 때만 사용될 수 있다고 설명한다. active-row 조회는 predicate를 명시적으로 포함해야 한다.

### 예시 2. active-only uniqueness를 일반 UNIQUE로 둔다

```sql
CREATE TABLE auth.users (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL UNIQUE,
    deleted_at timestamp with time zone
);
```

**나쁜 이유:**

- soft-deleted row까지 포함해 테이블 전체에서 email 중복을 막아 버린다
- "삭제된 이메일은 다시 쓸 수 있다"는 정책을 표현할 수 없다
- active subset uniqueness가 아니라 전체-table uniqueness다

PostgreSQL은 일부 row에만 적용되는 uniqueness restriction은 unique constraint로 쓸 수 없고, partial unique index로 표현해야 한다고 설명한다.

### 예시 3. partial unique index natural key를 FK target으로 사용하려고 한다

```sql
CREATE UNIQUE INDEX uq_users__email__active
    ON auth.users (email)
    WHERE deleted_at IS NULL;

CREATE TABLE auth.login_audit (
    user_email text REFERENCES auth.users(email)
);
```

**나쁜 이유:**

- FK 대상은 non-partial unique index 또는 PK/UNIQUE 제약이어야 한다
- active-row partial unique index는 FK target 자격이 없다
- soft delete와 natural key 참조를 섞으면 설계가 불안정해진다

PostgreSQL은 FK 참조 대상이 non-deferrable unique/primary key 제약 또는 non-partial unique index여야 한다고 설명한다.

### 예시 4. @OneToMany 컬렉션에 @SoftDelete를 붙인다

```java
@OneToMany(mappedBy = "user")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
private List<Session> sessions;
```

**나쁜 이유:**

- Hibernate 공식 지원 범위가 아니다
- `@OneToMany`에 `@SoftDelete`를 붙이면 예외가 난다
- 자식 엔티티 자체를 soft delete해야 한다

Hibernate는 `@OneToMany` association에 `@SoftDelete`를 붙이면 예외를 던진다고 설명한다.

### 예시 5. soft-deletable 엔티티를 batch delete로 지운다

```java
userRepository.deleteAllInBatch(users);
```

**나쁜 이유:**

- 단일 query physical delete가 일어난다
- persistence context가 DB와 동기화되지 않을 수 있다
- JPA cascade semantics와 lifecycle events도 존중되지 않는다
- soft delete semantics를 우회할 위험이 크다

Spring Data JPA는 `deleteAllInBatch(Iterable)`가 단일 query를 만들고, first level cache와 DB를 out of sync 상태로 만들 수 있으며, JPA cascade와 lifecycle event를 존중하지 않는다고 설명한다.

### 예시 6. restore를 uniqueness 검증 없이 수행한다

```sql
UPDATE auth.users
SET deleted_at = NULL
WHERE id = :userId;
```

**나쁜 이유:**

- 같은 이메일을 가진 다른 active row가 이미 있으면 active subset uniqueness를 깨뜨릴 수 있다
- restore는 단순 null 복원이 아니라 active set에 다시 들어가는 상태 전이다
- partial unique index와 충돌 가능성을 고려해야 한다

PostgreSQL unique partial index는 predicate를 만족하는 row subset 안에서 uniqueness를 강제한다. restore는 그 subset으로 다시 들어가는 행위다.
