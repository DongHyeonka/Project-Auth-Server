# time 타입 / 포맷 예시

이 문서는 [time 타입 / 포맷 기준](../../standards/language/time.md)을 코드 예시로 확인하기 위한 자료입니다.  
핵심 기준은 시간 값을 문자열이나 숫자로 들고 다니지 않고, 시점 / 날짜 / 시각 / 기간 / 시간대 의미에 맞는 `java.time` 타입으로 표현하는 것입니다.

## 좋은 예시 1: event timestamp는 Instant

```java
public record AuditEvent(
        String action,
        Long userId,
        Instant occurredAt
) {
}
```

왜 좋은가:

- timeline 위 한 점을 명확하게 표현한다.
- 로깅, 저장, 비교에 적합하다.

## 좋은 예시 2: 사람 기준 날짜 의미는 LocalDate / YearMonth

```java
public record UserProfile(
        LocalDate birthDate,
        YearMonth cardExpiry
) {
}
```

왜 좋은가:

- 시간대와 무관한 사람 기준 날짜 의미를 타입으로 드러낸다.
- 생일과 카드 만료월처럼 서로 다른 날짜 의미를 구분한다.

## 좋은 예시 3: 현재 시각은 Clock 기반

```java
public class TokenIssuer {

    private final Clock clock;

    public TokenIssuer(Clock clock) {
        this.clock = clock;
    }

    public Instant issueTime() {
        return Instant.now(clock);
    }

    public Instant expiryTime(Duration ttl) {
        return Instant.now(clock).plus(ttl);
    }
}
```

왜 좋은가:

- 현재 시각을 고정해 테스트하기 쉽다.
- static `now()` 호출이 코드 곳곳에 흩어지지 않는다.

## 좋은 예시 4: 시간 간격은 Duration

```java
private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
```

왜 좋은가:

- `1800` 같은 매직 숫자보다 의미가 분명하다.
- 초, 밀리초, 분 단위 혼동이 줄어든다.

## 좋은 예시 5: 외부 응답 포맷은 경계에서 처리

```java
String value = DateTimeFormatter.ISO_INSTANT.format(event.occurredAt());
```

왜 좋은가:

- 내부 로직은 `Instant`를 유지한다.
- 문자열 포맷은 serialization, logging, external API adapter 같은 boundary에서만 수행한다.

## 좋은 예시 6: 실제 zone 계산이 필요할 때만 ZonedDateTime

```java
ZonedDateTime reservationTime = localReservationTime.atZone(ZoneId.of("Asia/Seoul"));
```

왜 좋은가:

- 서울 지역 wall-clock 시간이라는 의미가 필요할 때만 zone을 붙인다.
- 시간대 규칙이 필요한 계산임을 코드에 드러낸다.

## 나쁜 예시 1: createdAt을 LocalDateTime으로 저장

```java
private LocalDateTime createdAt;
```

문제:

- 절대 시점이 아니라 zone/offset 없는 wall-clock 값이 된다.
- 시스템 간 교환, 저장, 비교에서 의미가 흔들린다.

개선:

```java
private Instant createdAt;
```

## 나쁜 예시 2: business logic에서 기본 시스템 zone 의존

```java
LocalDate today = LocalDate.now();
```

문제:

- JVM 기본 time-zone에 암묵적으로 의존한다.
- 테스트와 운영 환경에 따라 결과가 달라질 수 있다.

개선:

```java
LocalDate today = LocalDate.now(clock);
```

또는:

```java
LocalDate today = LocalDate.now(zoneId);
```

## 나쁜 예시 3: 문자열로 시간 비교

```java
if (request.startTime().compareTo("09:00") >= 0) {
    // open
}
```

문제:

- 타입 의미가 사라진다.
- 포맷 변화에 취약하다.

개선:

```java
if (!request.startTime().isBefore(LocalTime.of(9, 0))) {
    // open
}
```

## 나쁜 예시 4: legacy API 사용

```java
Date now = new Date();
Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + 1_800_000);
```

문제:

- 새 코드 기준으로 `java.time`보다 의미가 덜 명확하다.
- 시간 단위와 시스템 clock 의존이 코드에 흩어진다.

개선:

```java
Instant now = Instant.now(clock);
Instant expiresAt = now.plus(Duration.ofMinutes(30));
```

## 나쁜 예시 5: wall-clock 의미인데 Instant 남용

```java
public record StoreHours(
        Instant opensAt,
        Instant closesAt
) {
}
```

문제:

- 영업 시작/종료는 보통 지역 wall-clock 의미다.
- 절대 시점 타입이 도메인 의미를 흐린다.

개선:

```java
public record StoreHours(
        LocalTime opensAt,
        LocalTime closesAt
) {
}
```

## 나쁜 예시 6: Instant를 DTO에서 문자열로 직접 조립

```java
public record TokenResponse(
        String expiresAt
) {
    public static TokenResponse from(Instant expiresAt) {
        return new TokenResponse(expiresAt.toString());
    }
}
```

문제:

- DTO 조립 코드가 시간 포맷 정책을 직접 가진다.
- 응답 포맷 변경이 여러 DTO 생성 코드로 퍼질 수 있다.

개선 방향:

- response serialization 설정이나 전용 formatter 경계에서 포맷한다.
- 내부 모델과 유스케이스 결과는 `Instant` 같은 typed value를 유지한다.
