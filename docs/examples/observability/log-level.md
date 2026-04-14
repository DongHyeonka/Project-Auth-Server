# Log Level 예시

## 좋은 예시

### 예시 1. 최종 실패만 ERROR로 남긴다

```java
try {
    externalAuthClient.issueToken(command);
} catch (ExternalAuthException ex) {
    log.error("Failed to issue external auth token. provider={}, actorId={}", "auth-provider", command.actorId(), ex);
    throw ex;
}
```

**좋은 이유:**

- 최종 실패를 명확히 드러낸다
- 운영자가 바로 봐야 할 사건이다

### 예시 2. 재시도 후 성공은 WARN으로 남긴다

```java
log.warn("External auth request succeeded after retry. provider={}, actorId={}, attempts={}",
        "auth-provider", actorId, attemptCount);
```

**좋은 이유:**

- 즉시 실패는 아니지만 이상 징후다
- 운영 추적 가치가 있다

### 예시 3. 상세 분기 정보는 DEBUG에 둔다

```java
log.debug("Mapped external auth response to internal token result. provider={}, tokenType={}",
        "auth-provider", response.tokenType());
```

**좋은 이유:**

- 상세 흐름 파악용이다
- 기본 운영 레벨에서는 숨겨진다

## 나쁜 예시

### 예시 1. 예상 가능한 비즈니스 거절을 ERROR로 남긴다

```java
log.error("Duplicate email sign-up attempt. email={}", request.email());
```

**나쁜 이유:**

- 서버 장애처럼 과장된다
- 실제 운영 신호가 묻힌다

### 예시 2. 같은 예외를 여러 계층에서 모두 ERROR로 찍는다

```java
log.error("Repository failed", ex);
log.error("Service failed", ex);
log.error("Controller failed", ex);
```

**나쁜 이유:**

- 한 실패가 세 번 기록된다
- 검색/알림/분석 품질이 떨어진다

### 예시 3. production 상시 로그에 과도한 상세를 남긴다

```java
log.info("Request payload={}", requestBody);
log.info("Response payload={}", responseBody);
```

**나쁜 이유:**

- 노이즈가 많다
- 민감정보 노출 위험이 크다
- INFO 레벨 의미를 무너뜨린다
