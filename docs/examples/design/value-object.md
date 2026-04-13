# value object 예시

## 좋은 예시 1: 이메일 Value Object

```java
public record UserEmail(String value) {

    public UserEmail {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid email format");
        }
    }

    public static UserEmail from(String raw) {
        return new UserEmail(raw);
    }
}
```

**왜 좋은가:**

- 문자열 의미를 타입으로 끌어올린다
- 정규화와 검증이 한 곳에 모인다
- 값 기반 equality가 자연스럽다

## 좋은 예시 2: 금액 Value Object

```java
public record Money(BigDecimal amount) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }
}
```

**왜 좋은가:**

- 숫자 primitive를 그대로 흘리지 않는다
- scale/음수 금지 규칙이 타입에 들어간다
- 값 관련 행위가 같이 있다

## 좋은 예시 3: entity와 분리된 domain Value Object

```java
@Entity
@Table(name = "users")
public class UserJpaEntity {
    private String email;
}

public record UserEmail(String value) { ... }

public final class UserPersistenceMapper {
    public User toDomain(UserJpaEntity entity) {
        return User.restore(
                UserEmail.from(entity.getEmail())
        );
    }
}
```

**왜 좋은가:**

- persistence 문자열과 domain 의미 타입이 분리된다
- domain invariant를 mapper 경계에서 회복한다

## 나쁜 예시 1: identity를 가진 것을 Value Object처럼 사용

```java
public record User(Long id, String name) {}
```

**문제:**

- User는 identity가 본질인 entity일 가능성이 높다
- 값 객체로 만들면 의미가 흐려진다

## 나쁜 예시 2: mutable Value Object

```java
public class UserName {
    private String value;

    public void setValue(String value) {
        this.value = value;
    }
}
```

**문제:**

- 생성 후 불변이 아니다
- 검증/정규화 이후 상태가 깨질 수 있다

## 나쁜 예시 3: 의미 없는 래퍼

```java
public record NameString(String value) {}
```

**문제:**

- business meaning이 약하다
- 검증/정규화/행위가 전혀 없다
- 래퍼 비용만 생길 수 있다

## 나쁜 예시 4: Value Object에서 외부 의존

```java
public class UserEmail {
    public boolean exists(UserRepository repository) {
        return repository.existsByEmail(value);
    }
}
```

**문제:**

- 값 객체가 외부 의존과 오케스트레이션을 떠안는다
- 순수한 값 의미 타입이 아니다
