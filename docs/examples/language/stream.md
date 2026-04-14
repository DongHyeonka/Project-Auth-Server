# Stream 사용 예시

이 문서는 [Stream 사용 기준](../../standards/language/stream.md)을 코드 예시로 확인하기 위한 자료입니다.  
핵심 기준은 Stream을 집계, 변환, 검색 파이프라인에 쓰고, 외부 상태 변경이나 필수 부작용에는 쓰지 않는 것입니다.

## 좋은 예시 1: 조회 + 변환 + 불변 결과

```java
List<String> activeEmails = users.stream()
        .filter(User::isActive)
        .map(User::getEmail)
        .toList();
```

왜 좋은가:

- 집계/변환 파이프라인이다.
- side-effect가 없다.
- 결과가 명확하다.
- 읽는 사람이 "무엇을 만들었는지" 바로 이해할 수 있다.

## 좋은 예시 2: 존재 여부 판단

```java
boolean hasExpiredToken = tokens.stream()
        .anyMatch(Token::isExpired);
```

왜 좋은가:

- for 루프보다 의도가 직접적이다.
- short-circuit terminal operation이라 불필요한 순회를 줄일 수 있다.

## 좋은 예시 3: 그룹화

```java
Map<AuthProvider, List<User>> usersByProvider = users.stream()
        .collect(Collectors.groupingBy(User::getProvider));
```

왜 좋은가:

- grouping이 핵심인 aggregate operation이다.
- 외부 mutable map을 직접 관리하지 않는다.

## 좋은 예시 4: 숫자 집계는 primitive stream 사용

```java
int totalQuantity = orderLines.stream()
        .mapToInt(OrderLine::getQuantity)
        .sum();
```

왜 좋은가:

- 숫자 집계 의도가 분명하다.
- boxed `Integer` stream보다 표현이 명확하다.

## 좋은 예시 5: 구체 컬렉션 타입이 필요할 때만 toCollection

```java
LinkedHashSet<String> roles = authorities.stream()
        .map(Authority::getRole)
        .collect(Collectors.toCollection(LinkedHashSet::new));
```

왜 좋은가:

- 결과 타입 요구사항이 있을 때만 명시적으로 선택한다.
- `Collectors.toList()`의 구현/가변성에 기대지 않는다.

## 좋은 예시 6: I/O 기반 stream은 닫기

```java
try (Stream<String> lines = Files.lines(path)) {
    List<String> words = lines
            .flatMap(line -> Stream.of(line.split("\\s+")))
            .filter(word -> !word.isBlank())
            .toList();
}
```

왜 좋은가:

- I/O 기반 stream을 명시적으로 닫는다.
- transform pipeline과 resource lifecycle이 분리되어 있다.

## 나쁜 예시 1: 외부 mutable accumulator 사용

```java
List<String> emails = new ArrayList<>();
users.stream()
        .filter(User::isActive)
        .map(User::getEmail)
        .forEach(emails::add);
```

문제:

- 불필요한 side-effect가 있다.
- 병렬화나 리팩터링에 취약하다.
- reduction/collection으로 더 안전하게 표현할 수 있다.

개선:

```java
List<String> emails = users.stream()
        .filter(User::isActive)
        .map(User::getEmail)
        .toList();
```

## 나쁜 예시 2: 상태를 가진 람다

```java
AtomicInteger seq = new AtomicInteger(0);

List<UserView> result = users.stream()
        .map(user -> new UserView(seq.incrementAndGet(), user.getEmail()))
        .toList();
```

문제:

- 람다가 외부 상태에 의존한다.
- 병렬 스트림이나 리팩터링 시 의미가 불안정하다.

개선 방향:

- 순번이 비즈니스적으로 필요하면 스트림 밖에서 명시적으로 설계한다.
- 단순 변환이면 순번 생성을 제거한다.

## 나쁜 예시 3: 비즈니스 로직에서 peek 사용

```java
List<User> result = users.stream()
        .peek(user -> audit("USER_READ", user.getId()))
        .filter(User::isActive)
        .toList();
```

문제:

- `peek`를 필수 부작용 채널로 쓰고 있다.
- 최적화/재구성 시 기대가 깨질 수 있다.

개선 방향:

- audit가 필수라면 terminal boundary에서 명시적으로 처리한다.
- `peek`는 임시 디버깅 용도로만 제한한다.
