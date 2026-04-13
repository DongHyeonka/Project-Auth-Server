# Log Message Format 예시

## 좋은 예시

### 예시 1. 사건 설명 + key-value 필드를 함께 남긴다

```java
log.info("Created user. actorId={} userId={} requestPath={}",
        actorId, userId, requestPath);
```

**좋은 이유:**

- 사건이 짧게 드러난다
- 검색 가능한 핵심 키가 있다
- free text만으로 끝나지 않는다

### 예시 2. 실패 메시지에 운영 키를 먼저 담고 예외를 붙인다

```java
log.error("Failed external auth request. provider={} actorId={} durationMs={}",
        provider, actorId, durationMs, ex);
```

**좋은 이유:**

- 메시지 자체만 봐도 무엇이 실패했는지 알 수 있다
- stack trace는 추가 정보로 붙는다
- 운영 키가 빠지지 않는다

### 예시 3. 재시도/대체 경로도 명시적 사건으로 남긴다

```java
log.warn("Applied external auth fallback. provider={} actorId={} fallback={} durationMs={}",
        provider, actorId, "cached-public-key", durationMs);
```

**좋은 이유:**

- fallback 발생 사실이 바로 드러난다
- 이후 검색과 집계가 쉽다

### 예시 4. structured logging 전환을 고려한 키 이름을 쓴다

```java
log.info("Completed session cleanup. job={} deletedCount={} durationMs={}",
        "expired-session-cleanup", deletedCount, durationMs);
```

**좋은 이유:**

- 텍스트 로그에서도 구조가 보인다
- JSON 로그로 전환해도 의미가 유지된다

## 나쁜 예시

### 예시 1. 설명만 길고 검색 키가 없다

```java
log.info("The user registration process was completed successfully after all checks had been passed");
```

**나쁜 이유:**

- 누가, 어떤 요청에서, 어떤 리소스가 생성됐는지 알 수 없다
- 검색/집계가 어렵다

### 예시 2. 예외 메시지를 그대로 제목으로 쓴다

```java
log.error(ex.getMessage(), ex);
```

**나쁜 이유:**

- 사건 맥락이 없다
- 운영 키가 없다
- 예외 메시지 품질에 로그 제목이 종속된다

### 예시 3. 민감정보를 그대로 남긴다

```java
log.debug("Login request. email={} password={} accessToken={}",
        request.email(), request.password(), accessToken);
```

**나쁜 이유:**

- 민감정보가 원문으로 노출된다
- 디버그 로그라도 허용되지 않는다

### 예시 4. 같은 의미를 제각각 다른 키 이름으로 쓴다

```java
log.info("Created user. uid={} path={} timeMs={}", userId, requestPath, durationMs);
log.info("Deleted user. userId={} requestUri={} duration={}", userId, requestPath, durationMs);
```

**나쁜 이유:**

- 같은 의미의 키 이름이 섞인다
- 검색/집계/알람 규칙이 복잡해진다
