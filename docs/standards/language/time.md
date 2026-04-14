# time 타입 / 포맷 기준

## 목적

시간은 문자열이나 숫자 조합이 아니라 **의미에 맞는 타입**으로 표현한다.  
“시점”, “날짜”, “시간”, “벽시계 기준 날짜-시간”, “시간대 포함 날짜-시간”, “기간”을 서로 다른 개념으로 취급한다.

## 공식 의미

- `java.time`는 날짜/시간/instant/duration의 기본 API다.
- `java.time` 타입은 immutable, thread-safe 다.
- 시스템 경계를 넘는 저장/네트워크에는 ISO-8601 기반 `java.time` 타입을 우선 사용한다.
- `Instant`는 timestamp에 해당한다.
- `LocalDate`는 날짜만 표현한다.
- `LocalTime`은 시간만 표현한다.
- `LocalDateTime`은 날짜+시간이지만 offset/time-zone이 없다.
- `ZonedDateTime`은 time-zone까지 포함한 “full” date-time이다.
- `OffsetDateTime`은 UTC offset을 포함하며 네트워크 프로토콜/DB 접근에서 주로 쓰인다.
- 현재 시각은 `Clock`에서 얻을 수 있고, 이는 테스트를 쉽게 만든다.
- `now()` 계열은 시스템 clock과 기본 time-zone을 사용한다.

## 기본 규칙

### 1. 문자열 대신 타입으로 표현
시간/날짜를 내부 로직에서 `String`으로 들고 다니지 않는다.

금지:
- `"2026-04-12T10:15:30Z"`를 business field로 보관
- `"2026-04-12"`를 domain/application 로직에서 날짜 대신 사용
- `"09:00"`를 opening time 의미로 직접 비교

기본:
- 날짜 -> `LocalDate`
- 시각만 -> `LocalTime`
- 시점 -> `Instant`
- 날짜+시간(시간대 없음) -> `LocalDateTime`
- 시간대 포함 wall-clock -> `ZonedDateTime`
- offset 포함 경계 표현 -> `OffsetDateTime`
- 시간 간격 -> `Duration`
- 사람 기준 기간(일/월/년) -> `Period`

### 2. 시점(point in time)은 `Instant`를 기본값으로
로그 시각, 생성 시각, 만료 시각, 이벤트 발생 시각처럼 timeline 위 한 점이면 `Instant`를 우선한다.

예:
- `createdAt`
- `updatedAt`
- `expiresAt`
- `issuedAt`

### 3. 사람 기준 날짜/시간은 Local 타입 우선
시간대와 무관한 도메인 의미에는 Local 타입을 쓴다.

예:
- 생일 -> `LocalDate`
- 영업 시작 시각 -> `LocalTime`
- UI에서 입력한 예약 시각(아직 zone 미확정) -> `LocalDateTime`

### 4. `LocalDateTime`은 시점이 아니다
`LocalDateTime`은 offset/time-zone이 없으므로 절대적인 시점으로 저장/비교/교환할 때 기본값으로 쓰지 않는다.

금지 예:
- `createdAt`를 `LocalDateTime`으로 저장
- 외부 시스템과 절대 시각을 `LocalDateTime`으로 주고받기

### 5. `ZonedDateTime`은 정말 시간대 계산이 필요할 때만
실제 지역 시간대 규칙(DST 포함)을 고려해야 하는 계산만 `ZonedDateTime`을 쓴다.

예:
- 특정 도시/지역 wall-clock 기준 예약
- 사용자 time-zone 기준 만료/알림 계산

기본값은 아니다. 시간대는 복잡도를 크게 높인다.

### 6. 경계/프로토콜/DB에서는 `Instant` 또는 `OffsetDateTime`을 우선 검토
시스템 경계를 넘는 시간 값은 ISO-8601 기반 `java.time` 타입을 사용한다.

기본 선택:
- “절대 시점” 저장/전송 -> `Instant`
- offset이 포함된 wire/db 표현 필요 -> `OffsetDateTime`

### 7. 현재 시각은 `Clock` 기반으로 다룰 수 있게 설계
테스트 가능성이 중요한 코드에서는 `Instant.now()` / `LocalDate.now()` / `LocalDateTime.now()`를 직접 박지 않는다.

기본 방향:
- 현재 시각을 얻는 위치를 경계/서비스로 모은다
- 필요 시 `Clock`을 주입한다
- 테스트에서는 고정 clock을 사용한다

### 8. 기본 시스템 time-zone 의존 최소화
기본 시스템 time-zone을 암묵적으로 쓰는 호출은 신중히 제한한다.

기본 원칙:
- zone이 중요하면 `ZoneId`를 명시한다
- `now()` 계열의 기본 zone 의존은 casual code에서만 허용
- business logic, persistence, cross-system contract에서는 zone을 명시적으로 다룬다

### 9. 포맷/파싱은 경계에서만
날짜/시간 포맷팅과 파싱은 주로 boundary에서 수행한다.

예:
- controller/request binding
- response serialization
- external API adapter
- logging formatter

domain/application 내부에서는 typed value를 유지한다.

### 10. 기본 포맷은 ISO-8601 우선
새로운 커스텀 날짜 포맷을 기본값으로 만들지 않는다.  
시스템 간 교환은 ISO-8601을 우선한다.

### 11. `Duration`과 `Period`를 구분
- 기계적 시간 간격 -> `Duration`
- 사람 기준 달력 기간 -> `Period`

예:
- access token TTL -> `Duration`
- “30일 후”, “1개월 후” 같은 달력 의미 -> `Period`

### 12. legacy 시간 API는 새 코드에서 기본 금지
새 코드에서는 다음을 기본 금지한다.

- `java.util.Date`
- `java.util.Calendar`
- `java.sql.Timestamp`
- `TimeZone` 중심 설계
- `System.currentTimeMillis()`를 직접 흩뿌리는 패턴

기본은 `java.time` 사용이다.

### 13. 저장/조회/직렬화 기준을 명시
시간 값을 DB/JSON/API에 노출할 때는 다음을 명확히 한다.

- 어떤 타입을 저장하는가
- 어떤 zone/offset 가정을 하는가
- 어떤 포맷으로 직렬화하는가
- 정밀도(초/밀리초/나노초)를 어느 수준까지 쓸 것인가

### 14. 문자열 비교/부분 파싱으로 시간 로직 작성 금지
시간 판단을 문자열 조작으로 하지 않는다.

금지:
- `timestamp.startsWith(...)`
- `"09:00".compareTo(...)`
- substring으로 연/월/일 추출

## 프로젝트 기준 요약

- 시간은 타입으로 표현한다
- 시점은 `Instant`
- 날짜는 `LocalDate`
- 시각은 `LocalTime`
- `LocalDateTime`은 절대 시점 용도 금지
- 실제 time-zone 계산만 `ZonedDateTime`
- 경계/DB/네트워크는 `Instant` 또는 `OffsetDateTime`
- 현재 시각은 가능하면 `Clock` 기반
- 기본 시스템 time-zone 의존 최소화
- 포맷/파싱은 경계에서만
- 새 코드에서 legacy 시간 API 금지
