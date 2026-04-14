# Schema Structure 예시

## 좋은 예시

### 예시 1. 애플리케이션 전용 schema를 migration으로 만든다

```sql
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE auth.users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(320) NOT NULL
);
```

**좋은 이유:**

- 애플리케이션 객체가 public 과 분리된다
- schema 생성과 테이블 생성 위치가 명확하다
- PostgreSQL의 CREATE SCHEMA 와 qualified name 사용 방식에 맞다.

### 예시 2. ORM 기본 schema를 한 곳에서 맞춘다

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;
}
```

**좋은 이유:**

- 기본 schema를 연결/ORM 설정에서 맞춘다는 전제를 따른다
- 모든 엔티티에 같은 schema 문자열을 반복하지 않는다
- Hibernate는 schema를 따로 지정하지 않으면 현재 연결의 기본 schema를 사용한다.

### 예시 3. 정말 필요한 경우에만 특정 엔티티에 schema를 명시한다

```java
@Entity
@Table(schema = "auth_audit", name = "login_audit")
public class LoginAudit {
    @Id
    private Long id;
}
```

**좋은 이유:**

- 예외적 별도 schema 의도를 코드에서 드러낸다
- Hibernate가 지원하는 공식 매핑 방식이다.

## 나쁜 예시

### 예시 1. 기본 public 에 그냥 테이블을 만든다

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY
);
```

**나쁜 이유:**

- 애플리케이션 schema 의도가 없다
- search_path 와 public 기본값에 기대게 된다
- PostgreSQL 기본 동작상 unqualified create는 현재 스키마, 기본적으로는 public 에 들어갈 수 있다.

### 예시 2. 같은 schema를 모든 엔티티에 반복해서 박아 둔다

```java
@Entity
@Table(schema = "auth", name = "users")
public class User { ... }

@Entity
@Table(schema = "auth", name = "sessions")
public class Session { ... }

@Entity
@Table(schema = "auth", name = "login_histories")
public class LoginHistory { ... }
```

**나쁜 이유:**

- 기본 schema가 하나인데 코드 중복만 늘어난다
- schema 변경 시 수정 범위가 불필요하게 커진다
- Hibernate는 기본 schema 연결을 사용할 수 있다.

### 예시 3. search_path에 기대어 운영마다 다른 schema를 본다

```sql
SET search_path TO auth, public;
SELECT * FROM users;
```

**나쁜 이유:**

- 환경별 search_path 차이에 취약하다
- PostgreSQL 공식 문서도 search_path 는 해석 결과와 신뢰 경계를 바꾸므로 주의하라고 설명한다
