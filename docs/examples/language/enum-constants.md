# enum / constants 예시

이 문서는 [enum / constants 기준](../../standards/language/enum-constants.md)을 코드 예시로 확인하기 위한 자료입니다.  
핵심 기준은 고정된 의미 집합을 enum으로 표현하고, external code / display label / internal name을 섞지 않는 것입니다.

## 좋은 예시 1: 문자열 상수 대신 enum

```java
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    KAKAO
}

if (user.getProvider() == AuthProvider.LOCAL) {
    // local login flow
}
```

왜 좋은가:

- 고정된 값 집합을 타입으로 표현한다.
- 오타와 매직 스트링 분기를 줄인다.

## 좋은 예시 2: external code를 명시적 필드로 분리

```java
public enum AuthProvider {
    LOCAL("local"),
    GOOGLE("google"),
    KAKAO("kakao");

    private final String code;

    AuthProvider(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
```

왜 좋은가:

- `name()`에 외부 계약을 맡기지 않는다.
- 내부 enum 이름 변경과 외부 계약을 분리할 수 있다.

## 좋은 예시 3: EnumSet 사용

```java
private static final EnumSet<AuthProvider> SOCIAL_PROVIDERS =
        EnumSet.of(AuthProvider.GOOGLE, AuthProvider.KAKAO);
```

왜 좋은가:

- enum 집합이라는 의도가 직접 드러난다.
- 비트 플래그나 일반 `Set`보다 타입 안전하고 목적에 맞다.

## 좋은 예시 4: EnumMap 사용

```java
private final EnumMap<AuthProvider, OAuthClient> clients =
        new EnumMap<>(AuthProvider.class);
```

왜 좋은가:

- enum key 전용 자료구조라는 점이 명확하다.
- 일반 `HashMap`보다 목적에 더 잘 맞는다.

## 좋은 예시 5: 진짜 상수만 상수로 둠

```java
private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
private static final List<String> PUBLIC_PATHS = List.of("/", "/login");
```

왜 좋은가:

- 값이 immutable이다.
- `static final`뿐 아니라 실제 의미도 안정적이다.

## 나쁜 예시 1: ordinal 저장/분기

```java
int providerCode = provider.ordinal();
```

문제:

- enum 순서 변경이나 값 추가에 취약하다.
- stable contract가 아니다.

개선:

```java
String providerCode = provider.code();
```

## 나쁜 예시 2: name/toString 문자열 비교

```java
if (provider.name().equals("GOOGLE")) {
    // google login flow
}
```

문제:

- enum 의미 비교를 문자열 비교로 내린다.
- 타입 안전성이 사라진다.

개선:

```java
if (provider == AuthProvider.GOOGLE) {
    // google login flow
}
```

## 나쁜 예시 3: 잡다한 constants class

```java
public final class AppConstants {

    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_GOOGLE = "GOOGLE";
    public static final String PROVIDER_KAKAO = "KAKAO";

    private AppConstants() {
    }
}
```

문제:

- 고정된 의미 집합을 타입으로 표현하지 않는다.
- 문자열 오타와 분기 누락에 취약하다.

개선:

```java
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    KAKAO
}
```

## 나쁜 예시 4: mutable collection을 상수처럼 사용

```java
private static final Set<String> PUBLIC_PATHS = new HashSet<>();
```

문제:

- `static final`이어도 내부 상태는 바뀔 수 있다.
- 진짜 상수라고 보기 어렵다.

개선:

```java
private static final Set<String> PUBLIC_PATHS = Set.of("/", "/login");
```

## 나쁜 예시 5: default로 enum 추가 누락 숨김

```java
return switch (provider) {
    case LOCAL -> localHandler();
    default -> socialHandler();
};
```

문제:

- 새 enum 값이 생겨도 의도치 않게 `default`에 흡수될 수 있다.
- 분기 누락이 컴파일 시점에 드러나기 어렵다.

개선:

```java
return switch (provider) {
    case LOCAL -> localHandler();
    case GOOGLE, KAKAO -> socialHandler();
};
```

## 나쁜 예시 6: null 회피용 UNKNOWN 남용

```java
public enum AuthProvider {
    UNKNOWN,
    LOCAL,
    GOOGLE,
    KAKAO
}
```

문제:

- `UNKNOWN`이 실제 비즈니스 상태가 아니라면 의미 없는 상태가 생긴다.
- 단순 null 회피가 enum 모델에 섞인다.

개선 방향:

- boundary 입력은 검증하거나 nullable로 명시한다.
- 조회 결과의 부재는 필요하면 `Optional<AuthProvider>`로 표현한다.
- 실제 비즈니스 상태일 때만 `UNKNOWN` 또는 `UNSPECIFIED`를 둔다.
