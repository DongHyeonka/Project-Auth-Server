# Optional 사용 기준

## 목적

`Optional`은 값의 부재를 **메서드 반환 타입에서 명시적으로 표현**할 때 사용한다.  
`null`을 감추기 위한 만능 래퍼로 쓰지 않는다.

## 공식 의미

- `Optional`은 값이 있을 수도 없을 수도 있는 non-null 컨테이너다.
- `Optional`은 **주로 메서드 반환 타입**으로 사용하도록 의도되었다.
- `Optional` 변수 자체는 `null`이면 안 된다.
- `Optional`은 value-based class이므로 동기화나 `==` 같은 identity-sensitive 사용을 하지 않는다.

## 기본 규칙

### 1. 기본값은 반환 타입으로만 사용

다음 경우에만 `Optional<T>`를 우선 검토한다.

- 조회 결과가 없을 수 있는 단건 반환
- 파싱/탐색/검색 결과가 없을 수 있는 반환
- 호출자가 "없음"을 명시적으로 처리해야 하는 API

예:

- `findById`
- `findUserByEmail`
- `parseXxx`
- `resolveCurrentTenant`

### 2. 필드 타입으로는 기본 금지

`Optional`을 엔티티 필드, DTO 필드, 설정 필드, 상태 저장 필드로 두지 않는다.

이유:

- 공식적으로 `Optional`은 주로 반환 타입 용도다.
- 상태 저장 필드에서는 nullable field, value object, 별도 타입, 또는 명시적 상태 모델이 더 적합하다.
- nullable `Optional`, empty `Optional`, present `Optional`의 혼합은 의미를 더 복잡하게 만든다.

### 3. 파라미터 타입으로는 기본 금지

메서드/생성자 파라미터에 `Optional<T>`를 직접 받지 않는다.

대신 아래를 우선 검토한다.

- 메서드 오버로드
- 별도 명시적 파라미터 타입
- nullable + 명확한 nullability annotation
- 요청 객체/커맨드 객체로 캡슐화

예외:

- 외부 라이브러리 API와의 일관성을 맞춰야 하는 경우
- 함수형 조합 API에서 정말 표현력이 좋아지는 경우

### 4. 절대 금지: `Optional`을 null로 사용

`Optional<T>` 타입 변수/반환값/파라미터에 `null`을 쓰지 않는다.

금지 예:

```java
return null; // Optional<T> 반환 메서드
Optional<User> user = null;
someMethod(null); // Optional<User> 파라미터
```

없음은 반드시 `Optional.empty()`로 표현한다.

### 5. `get()`은 기본 금지

`get()`은 값이 없으면 예외를 던진다.  
공식 문서도 preferred alternative로 `orElseThrow()`를 제시한다.

기본 규칙:

- `get()`은 테스트 코드나 명백한 증명 직후의 제한된 상황이 아니면 사용하지 않는다.
- 업무 코드에서는 `map`, `flatMap`, `filter`, `ifPresent`, `orElse`, `orElseGet`, `orElseThrow`를 사용한다.

### 6. `isPresent()` + `get()` 조합은 기본 금지

다음 패턴은 기본 금지한다.

```java
if (opt.isPresent()) {
    return opt.get();
}
```

대신 아래를 우선 검토한다.

- `map`
- `ifPresent`
- `orElse`
- `orElseGet`
- `orElseThrow`

### 7. 기본값 계산 비용이 있으면 `orElseGet`

단순 상수/기존 값이면 `orElse`를 검토한다.  
계산/조회/생성 비용이 있거나 side-effect 가능성이 있으면 `orElseGet`을 사용한다.

기본 원칙:

- expensive default는 `orElseGet`
- 단순 literal/default object는 `orElse` 검토

### 8. nested Optional 금지

`Optional<Optional<T>>` 형태를 만들지 않는다.

- mapper가 다시 `Optional`을 반환하면 `map` 대신 `flatMap`을 사용한다.
- 계층 탐색은 `map(...).flatMap(...)` 또는 `flatMap(...)` 조합으로 평탄화한다.

### 9. `map`은 값 변환, `ifPresent`는 최종 부작용

- 변환/계산/연결은 `map`, `flatMap`, `filter`를 사용한다.
- 최종 경계 side-effect는 `ifPresent`, `ifPresentOrElse`를 사용한다.

반환값을 쓰지 않을 때 `map`을 사용하지 않는다.

### 10. 컬렉션/스트림은 Optional보다 빈 표현 우선

컬렉션/배열/스트림 결과는 `Optional<List<T>>`보다 빈 컬렉션/빈 스트림을 우선한다.

기본 규칙:

- 단건 부재 -> `Optional<T>`
- 다건 부재 -> empty collection / empty stream

### 11. Optional은 직렬화/DTO 경계 기본 금지

Request/Response DTO, JPA Entity, 설정 프로퍼티 객체에 `Optional` 필드를 두지 않는다.

이유:

- 경계 타입은 직렬화/바인딩/스키마 명확성이 우선이다.
- `Optional`은 표현 모델보다 API 결과 표현 쪽에 더 적합하다.

### 12. primitive optional은 정말 필요할 때만

`OptionalInt`, `OptionalLong`, `OptionalDouble`은 primitive 부재 표현이 정말 중요한 경우에만 사용한다.  
일반 업무 도메인에서는 보통 `Optional<T>` 또는 명시적 값 객체가 더 읽기 쉽다.

## Spring / Repository 기준

- 단건 조회 결과 없음은 `Optional<T>`를 우선 검토한다.
- 컬렉션 반환은 `null` 대신 empty collection을 반환한다.
- repository package의 nullability는 `@NonNullApi`, `@NonNull`, `@Nullable` 같은 도구와 함께 설계한다.

## 프로젝트 기준 요약

- `Optional`은 기본적으로 반환 타입에만 사용한다.
- 필드/파라미터는 기본 금지한다.
- `Optional` 자체를 `null`로 두지 않는다.
- `get()`과 `isPresent() + get()`은 기본 금지한다.
- 값 변환은 `map`, `flatMap`, `filter`를 사용한다.
- 최종 부작용은 `ifPresent`, `ifPresentOrElse`를 사용한다.
- 다건 결과는 `Optional<List<T>>` 대신 empty collection을 사용한다.
