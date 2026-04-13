# Stream 사용 기준

## 목적

`Stream`은 컬렉션을 직접 조작하는 도구가 아니라, 데이터 소스에 대한 집계/변환/검색 파이프라인을 선언적으로 표현하는 도구로 사용한다.

## 사용할 때

다음 중 하나에 해당하면 `Stream`을 우선 검토한다.

- filter / map / flatMap / grouping / reduction처럼 **집계 파이프라인**이 중심일 때
- “무엇을 만들 것인가”가 분명하고, 루프보다 **의도가 더 잘 드러날 때**
- `anyMatch`, `findFirst`, `max`, `min`, `sum`, `groupingBy`, `partitioningBy` 같은 **집계 연산**이 핵심일 때
- 결과를 새로운 컬렉션이나 요약값으로 만드는 작업일 때

## 사용하지 않을 때

다음 중 하나에 해당하면 일반 `for` / `for-each`를 기본값으로 사용한다.

- 핵심이 **부작용(side-effect)** 인 경우
- 인덱스 기반 접근이 본질인 경우
- 중간 분기/예외 처리/조기 탈출이 많아져 파이프라인이 오히려 읽기 어려운 경우
- 디버깅이나 상태 변경이 중심인 경우
- 성능 민감 경로인데 스트림이 더 낫다는 측정 근거가 없는 경우

## 핵심 규칙

### 1. source를 수정하지 않는다
스트림 실행 중 source collection/data source를 수정하지 않는다.

- 스트림 내부 람다에서 source를 변경하지 않는다
- 외부에서 동시에 source를 수정하지 않는다
- source 변경이 필요하면 스트림 밖에서 먼저 끝내고, 새 스트림을 만든다

### 2. 람다는 non-interfering, stateless를 기본값으로 한다
`filter`, `map`, `sorted`, `reduce`, `collect` 등에 넘기는 람다는 기본적으로 상태를 들고 있지 않아야 하며, source나 외부 공유 상태를 변경하지 않아야 한다.

금지 예:
- 외부 `List`에 `forEach`로 accumulate
- `AtomicInteger` 같은 외부 상태를 카운터처럼 밀어넣기
- 람다 안에서 source collection 변경

### 3. side-effect에 의존하지 않는다
`forEach` / `forEachOrdered`를 제외하면, 스트림 구현은 최적화를 위해 일부 연산이나 람다 호출을 생략할 수 있다.  
따라서 비즈니스 로직은 side-effect가 아니라 **reduction / collection** 으로 표현한다.

### 4. 한 번 사용한 Stream은 재사용하지 않는다
terminal operation 이후 스트림은 소모된다.  
같은 데이터를 다시 순회하려면 source에서 새 stream을 만든다.

### 5. stateful intermediate operation은 비용을 의식한다
`sorted`, `distinct`, 일부 `limit/skip` 조합은 비용이 크거나 버퍼링이 필요할 수 있다.  
정렬/중복 제거는 정말 필요할 때만 넣는다.

### 6. 기본은 sequential stream이다
기본값은 `stream()`이다.  
`parallel()` / `parallelStream()`은 아래를 모두 만족할 때만 검토한다.

- 병렬화 이득이 측정됨
- 공유 상태/side-effect 없음
- 순서 보장이 중요하지 않거나 비용을 감수 가능
- collector/연산이 병렬 친화적임

### 7. `forEach`는 결과 생성이 아니라 최종 경계 부작용에만 쓴다
`forEach`는 보통 아래와 같은 최종 경계에서만 허용한다.

- 로그 출력
- 이벤트 발행
- 외부 시스템 호출
- 이미 계산된 결과를 최종 전달

컬렉션 생성/집계는 `collect`, `reduce`, `toList` 등으로 표현한다.

### 8. `peek`는 디버깅용으로만 제한한다
`peek`는 비즈니스 로직, 상태 변경, 필수 검증 로직에 사용하지 않는다.  
임시 디버깅 후 제거를 기본으로 한다.

### 9. 컬렉션 결과 규칙을 명확히 한다
- 수정 불가능한 결과가 목적이면 `toList()` 또는 `Collectors.toUnmodifiableList()`를 우선 검토한다
- 구체 컬렉션 타입이 필요하면 `Collectors.toCollection(...)`을 사용한다
- `Collectors.toList()` 결과를 mutable이라고 가정하지 않는다

### 10. 숫자 집계는 primitive stream을 우선 검토한다
합계/평균/최댓값/최솟값 중심이면 `mapToInt`, `mapToLong`, `mapToDouble`을 우선 검토해 boxing 비용과 표현 복잡도를 줄인다.

### 11. I/O 기반 stream은 닫는다
`Files.lines(...)` 같은 I/O 기반 stream은 `try-with-resources`로 닫는다.

## 프로젝트 기준 요약

- 집계/변환/검색 파이프라인이면 Stream
- 부작용/복잡한 제어 흐름이면 loop
- source 변경 금지
- 외부 mutable state 의존 금지
- `forEach`로 수집 금지
- `parallel()`은 측정 기반으로만 허용
- 결과 컬렉션의 mutability를 명시적으로 선택
