# collections / immutability 예시

이 문서는 [collections / immutability 기준](../../standards/language/collections-immutability.md)을 코드 예시로 확인하기 위한 자료입니다.  
핵심 기준은 컬렉션을 immutable-first로 다루고, 변경이 필요한 로컬 조립 단계가 끝나면 경계를 넘기기 전에 수정 불가 snapshot으로 고정하는 것입니다.

## 좋은 예시 1: 생성자에서 defensive copy

```java
public class RolePolicy {

    private final List<String> allowedRoles;

    public RolePolicy(List<String> allowedRoles) {
        this.allowedRoles = List.copyOf(allowedRoles);
    }

    public List<String> allowedRoles() {
        return allowedRoles;
    }
}
```

왜 좋은가:

- 외부에서 넘긴 mutable list를 그대로 보관하지 않는다.
- 내부 필드를 안정된 snapshot으로 고정한다.

## 좋은 예시 2: 상수성 데이터는 of 사용

```java
private static final Set<String> PUBLIC_PATHS = Set.of(
        "/",
        "/login",
        "/swagger-ui.html"
);
```

왜 좋은가:

- 상수 컬렉션 의도가 분명하다.
- `null`과 중복을 조기에 차단한다.

주의:

- 순서를 기대하면 `List.of`가 더 적합할 수 있다.

## 좋은 예시 3: stream 결과를 수정 불가로 고정

```java
List<String> activeEmails = users.stream()
        .filter(User::isActive)
        .map(User::getEmail)
        .toList();
```

왜 좋은가:

- 결과가 읽기 전용이라는 의도가 분명하다.
- 후속 코드가 실수로 수정하지 못한다.

## 좋은 예시 4: mutable 조립 후 경계에서 snapshot

```java
List<String> buildScopes(User user) {
    List<String> scopes = new ArrayList<>();
    scopes.add("profile");
    if (user.isAdmin()) {
        scopes.add("admin");
    }
    return List.copyOf(scopes);
}
```

왜 좋은가:

- 로컬 조립 단계에서는 mutable 컬렉션을 실용적으로 사용한다.
- 반환 시점에는 안정된 snapshot으로 바꾼다.

## 좋은 예시 5: 구체 mutable 결과가 필요하면 명시

```java
List<UserDto> result = users.stream()
        .map(UserMapper::toDto)
        .collect(Collectors.toCollection(ArrayList::new));
```

왜 좋은가:

- mutable 결과가 필요하다는 점을 코드에 드러낸다.
- `Collectors.toList()`의 mutability를 가정하지 않는다.

## 나쁜 예시 1: 내부 mutable collection 그대로 노출

```java
public class UserGroup {

    private final List<User> users = new ArrayList<>();

    public List<User> getUsers() {
        return users;
    }
}
```

문제:

- 외부에서 내부 상태를 직접 수정할 수 있다.
- 캡슐화가 깨진다.

개선:

```java
public List<User> getUsers() {
    return List.copyOf(users);
}
```

## 나쁜 예시 2: unmodifiable view를 immutable로 착각

```java
List<String> source = new ArrayList<>();
source.add("A");

List<String> readOnly = Collections.unmodifiableList(source);
source.add("B");
```

문제:

- `readOnly`는 immutable snapshot이 아니라 view다.
- `source`가 바뀌면 `readOnly`도 바뀐다.

개선:

```java
List<String> readOnly = List.copyOf(source);
```

## 나쁜 예시 3: null collection 반환

```java
public List<Role> findRoles(Long userId) {
    if (userId == null) {
        return null;
    }
    // ...
}
```

문제:

- 호출자마다 null-check를 강요한다.
- 컬렉션 결과의 계약이 흐려진다.

개선:

- empty list를 반환한다.
- 또는 입력 자체를 경계에서 검증한다.

## 나쁜 예시 4: 순서를 기대하면서 Set.of 사용

```java
Set<String> statuses = Set.of("NEW", "PROCESSING", "DONE");
String first = statuses.iterator().next();
```

문제:

- `Set.of` iteration order를 비즈니스 로직에 기대고 있다.
- JVM 실행마다 순서가 달라질 수 있다.

개선:

```java
List<String> statuses = List.of("NEW", "PROCESSING", "DONE");
String first = statuses.getFirst();
```

## 나쁜 예시 5: shallow immutability 오해

```java
List<UserProfile> profiles = List.copyOf(sourceProfiles);
profiles.get(0).changeNickname("new-name");
```

문제:

- 컬렉션은 수정 불가지만 원소는 mutable이라 상태가 바뀔 수 있다.
- 공유 상태 안정성을 보장하지 못한다.

개선 방향:

- immutable element를 사용한다.
- mutable element는 공유하지 않도록 복사하거나 변환한다.
