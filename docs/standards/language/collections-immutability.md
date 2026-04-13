# collections / immutability 기준

## 목적

컬렉션은 기본적으로 **immutable-first**로 다룬다.  
변경이 꼭 필요한 로컬 조립 단계에서만 mutable 컬렉션을 허용하고, 경계를 넘길 때는 수정 불가 snapshot 또는 명시적 불변 구조로 바꾼다.

## 공식 의미

- JDK의 `List.of`, `Set.of`, `Map.of`, `copyOf`, `toUnmodifiable*`는 수정 불가 컬렉션을 만든다.
- `Collections.unmodifiableXXX`는 원본 컬렉션을 감싼 view일 뿐이다. 원본이 바뀌면 view도 바뀐다.
- 컬렉션이 수정 불가여도 원소가 mutable이면 내용이 바뀐 것처럼 보일 수 있다.
- `List.of` / `List.copyOf` 계열은 null을 허용하지 않는다.
- `Set.of`와 `Map.of` 계열은 null을 허용하지 않으며, 중복 원소/중복 키를 허용하지 않는다.
- `Set.of`, `Map.of`, `Map.ofEntries`, `toUnmodifiableSet`, `toUnmodifiableMap`는 iteration order가 JVM 실행마다 달라질 수 있다.

## 기본 규칙

### 1. 기본은 immutable-first
다음 경우에는 mutable보다 immutable/unmodifiable 결과를 기본값으로 사용한다.

- 상수 컬렉션
- 설정/정책/권한 목록
- 외부에 반환하는 결과
- 공유되는 데이터
- 생성 후 더 이상 바뀌지 않아야 하는 상태

### 2. mutable 컬렉션은 로컬 조립 단계에서만 허용
다음 경우에는 mutable 컬렉션을 허용한다.

- 여러 source를 모아 결과를 만드는 임시 버퍼
- 반복적으로 add/remove가 필요한 내부 알고리즘
- aggregate/entity 내부에서 실제 상태 변경이 본질인 경우

단, 경계를 넘기기 전에 immutable/unmodifiable로 바꾼다.

### 3. 경계를 넘길 때는 defensive copy를 우선
다음 상황에서는 원본 컬렉션을 그대로 넘기지 않는다.

- 생성자에 받아서 필드로 저장할 때
- getter/응답 DTO/결과 객체로 반환할 때
- 다른 레이어로 넘길 때

기본 선택:
- snapshot이 목적 -> `List.copyOf`, `Set.copyOf`, `Map.copyOf`
- 작은 상수 컬렉션 -> `List.of`, `Set.of`, `Map.of`
- stream 수집 결과를 수정 불가로 고정 -> `Collectors.toUnmodifiableList/Set/Map`

### 4. `Collections.unmodifiableXXX`는 “view”가 필요할 때만
이 API는 기본 선택이 아니다.

허용되는 경우:
- 원본과 동기화되는 read-only view가 정말 필요할 때
- legacy API와의 호환 때문에 wrapper view가 필요한 경우

기본 금지 이유:
- 원본이 바뀌면 view도 바뀐다
- defensive copy나 true immutable 의도와 다르다

### 5. `copyOf`를 snapshot 기본값으로 사용
이미 가지고 있는 mutable collection을 안전하게 보관/반환해야 하면 `copyOf`를 우선 검토한다.

예:
- 생성자에서 받은 list를 필드에 저장
- service 결과를 외부에 반환
- mapper 결과를 response/domain에 전달

### 6. 컬렉션 null 금지, empty 우선
컬렉션 필드/반환값/파라미터는 가능하면 null을 금지한다.

기본값:
- 없음 -> empty list / set / map
- null collection 금지

### 7. 원소의 immutability를 따로 본다
컬렉션만 수정 불가여도 원소가 mutable이면 완전한 불변이 아니다.

기본 원칙:
- 공유되는 컬렉션은 가능하면 immutable element를 담는다
- mutable element를 담는 경우 “shallow immutable only”라는 점을 의식한다
- 외부에서 element mutation이 가능한 구조를 장기 공유 상태로 두지 않는다

### 8. 결과 컬렉션의 의미를 명시적으로 선택
- 순서가 중요하면 `List`
- 중복 제거가 목적이면 `Set`
- key lookup이 목적이면 `Map`

불변이 목적이라면:
- `List.copyOf`
- `Set.copyOf`
- `Map.copyOf`
- `Collectors.toUnmodifiable*`

를 우선 검토한다.

### 9. iteration order를 가정하지 않는다
`Set.of`, `Map.of`, `Map.ofEntries`, `toUnmodifiableSet`, `toUnmodifiableMap`는 iteration order가 랜덤화될 수 있다.  
순서가 중요하면 `List`나 순서를 보장하는 별도 컬렉션 타입을 명시적으로 사용한다.

### 10. `Collectors.toList()` 결과를 mutable이라고 가정하지 않는다
수집 결과의 mutability가 중요하면 명시적으로 선택한다.

- 수정 불가 결과 필요 -> `stream.toList()` 또는 `Collectors.toUnmodifiableList()`
- 구체 mutable 타입 필요 -> `Collectors.toCollection(ArrayList::new)` 등

### 11. getter는 내부 mutable collection을 노출하지 않는다
다음 패턴을 금지한다.

- 내부 `ArrayList` 참조를 그대로 반환
- 생성자에서 받은 collection 참조를 그대로 필드에 저장
- 외부에서 수정 가능한 map/set/list를 그대로 보관

### 12. domain/application에서 컬렉션은 가능한 한 non-null + stable
도메인/유스케이스 내부에서는:
- null collection 금지
- mutable shared state 최소화
- 변경 가능성이 없다면 불변 구조로 고정

## 프로젝트 기준 요약

- 기본은 immutable-first
- mutable은 로컬 조립 단계에서만 허용
- 경계에서는 `copyOf`/`of`/`toUnmodifiable*`를 기본값으로 사용
- `Collections.unmodifiableXXX`는 view가 필요할 때만 제한적으로 사용
- null collection 대신 empty collection
- 원소가 mutable이면 컬렉션만 막아도 완전한 불변이 아님
- iteration order가 중요한 곳에서 `Set.of`/`Map.of` 계열 순서를 가정하지 않음
