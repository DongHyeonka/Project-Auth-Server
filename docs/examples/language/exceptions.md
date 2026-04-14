# exceptions 예시

이 문서는 [exceptions 기준](../../standards/language/exceptions.md)을 코드 예시로 확인하기 위한 자료입니다.  
핵심 기준은 예외를 정상 흐름 제어가 아니라 예외 상황 전달 수단으로 사용하고, catch는 번역 / 문맥 추가 / 복구 목적이 있을 때만 두는 것입니다.

## 좋은 예시 1: 기술 예외를 계층 예외로 번역하면서 cause 보존

```java
try {
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return parse(response.body());
} catch (IOException exception) {
    throw new InfrastructureException(
            InfrastructureErrorCode.EXTERNAL_API_FAILED,
            "Failed to call external API",
            exception
    );
} catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    throw new InfrastructureException(
            InfrastructureErrorCode.EXTERNAL_API_FAILED,
            "External API call was interrupted",
            exception
    );
}
```

왜 좋은가:

- broad catch가 아니다.
- `InterruptedException`을 별도로 처리한다.
- cause를 보존한다.
- 기술 실패를 infrastructure 의미로 번역한다.

## 좋은 예시 2: try-with-resources 사용

```java
try (InputStream in = Files.newInputStream(path)) {
    return objectMapper.readValue(in, Payload.class);
}
```

왜 좋은가:

- 자원 해제를 자동화한다.
- close 중 예외가 발생해도 suppressed exception으로 보존될 수 있다.

## 좋은 예시 3: 상위 경계에서만 broad catch

```java
try {
    return useCase.execute(command);
} catch (ApplicationException exception) {
    return errorResponse(exception.getCode(), exception.getMessage());
} catch (Exception exception) {
    log.error("Unhandled exception while processing request", exception);
    return errorResponse("INTERNAL_SERVER_ERROR", "Unexpected server error");
}
```

왜 좋은가:

- 최상위 boundary에서 마지막 방어선으로만 broad catch를 쓴다.
- 내부 계층에서는 더 구체적인 예외 처리를 유지한다.
- 예상 가능한 application 예외와 예상하지 못한 실패를 구분한다.

## 좋은 예시 4: checked 예외 rollback 필요 시 명시

```java
@Transactional(rollbackFor = IOException.class)
public void importUsers(Path path) throws IOException {
    // import users from file
}
```

왜 좋은가:

- Spring 기본 rollback 규칙을 명시적으로 보완한다.
- checked exception이 rollback 대상인지 계약으로 드러난다.

## 좋은 예시 5: 테스트는 assertThrows 우선

```java
IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> service.createUser(command)
);
```

왜 좋은가:

- `try-catch + fail` 패턴보다 의도가 직접적이다.
- 예외 객체를 받아 메시지나 상태를 추가로 검증할 수 있다.

## 나쁜 예시 1: broad catch + 삼키기

```java
try {
    saveUser(user);
} catch (Exception exception) {
}
```

문제:

- 예외가 사라진다.
- 디버깅이 어려워진다.
- interruption 같은 중요한 신호도 놓칠 수 있다.

개선 방향:

- 복구할 수 있는 구체 예외만 catch한다.
- 계층 예외로 번역하거나, 문맥을 붙여 다시 던진다.
- 정말 무시해야 한다면 이유를 남기고 logging / metrics / 상태 기록 중 하나를 수행한다.

## 나쁜 예시 2: printStackTrace 후 계속 진행

```java
try {
    sync();
} catch (IOException exception) {
    exception.printStackTrace();
}
```

문제:

- 운영 로그 정책을 깨뜨린다.
- 실패를 구조적으로 전달하지 못한다.

개선 방향:

- logging framework로 기록한다.
- 또는 계층 예외로 번역해 상위 boundary로 전달한다.

## 나쁜 예시 3: finally에서 return

```java
try {
    return load();
} finally {
    return fallback();
}
```

문제:

- try 블록 결과와 예외를 덮어쓴다.
- 실제 실패가 호출자에게 전달되지 않을 수 있다.

개선 방향:

- `finally`는 정리 작업만 수행한다.
- fallback이 필요하면 catch나 명시적 분기에서 처리한다.

## 나쁜 예시 4: InterruptedException 뭉개기

```java
try {
    queue.take();
} catch (Exception exception) {
    throw new IllegalStateException(exception);
}
```

문제:

- interruption을 별도 의미로 처리하지 않는다.
- 스레드 인터럽트 상태를 잃을 수 있다.

개선:

```java
try {
    queue.take();
} catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Interrupted while waiting for queue item", exception);
}
```

## 나쁜 예시 5: 너무 넓은 일반 예외 던지기

```java
throw new RuntimeException("bad request");
```

문제:

- 의미가 너무 넓다.
- 호출자가 어떤 실패인지 이해하기 어렵다.
- 경계에서 일관된 에러 코드나 응답으로 번역하기 어렵다.

개선 방향:

- 계약 위반이면 `IllegalArgumentException` 같은 구체 예외를 사용한다.
- 계층 의미가 있으면 domain / application / infrastructure 예외로 표현한다.

## 나쁜 예시 6: 정상적인 결과 없음에 예외 사용

```java
public User findUser(Long userId) {
    return userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("user not found"));
}
```

문제:

- 결과 없음이 정상적인 조회 결과일 수 있는데 예외로만 표현한다.
- 호출자가 부재를 처리할 수 있는 선택지를 잃는다.

개선 방향:

- 단건 조회의 부재가 정상 흐름이면 `Optional<User>`를 반환한다.
- 유스케이스 계약상 반드시 있어야 하는 값이면 구체적인 application 예외로 번역한다.
