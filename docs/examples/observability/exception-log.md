# Exception Log 예시

## 좋은 예시

### 예시 1. 대표 실패만 ERROR로 남긴다

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ExternalAuthUnavailableException.class)
    public ResponseEntity<ApiResult<Void>> handleExternalAuthUnavailable(
            ExternalAuthUnavailableException ex,
            HttpServletRequest request
    ) {
        log.error("Failed request. requestPath={} method={} errorCode={} actorId={}",
                request.getRequestURI(),
                request.getMethod(),
                ErrorCode.UPSTREAM_AUTH_SERVER_UNAVAILABLE.code(),
                request.getAttribute("actorId"),
                ex);

        ErrorCode errorCode = ErrorCode.UPSTREAM_AUTH_SERVER_UNAVAILABLE;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode));
    }
}
```

**좋은 이유:**

- 대표 ERROR 로그가 한 곳에 모인다
- 메시지와 응답 코드가 분리된다
- 운영 키와 stack trace가 함께 남는다

### 예시 2. 재시도 중간 실패는 WARN 또는 DEBUG로만 남긴다

```java
try {
    return externalAuthClient.issueToken(command);
} catch (SocketTimeoutException ex) {
    log.warn("External auth attempt failed. provider={} actorId={} attempt={}",
            "keycloak", command.actorId(), attemptNumber);
    throw ex;
}
```

**좋은 이유:**

- 중간 실패를 곧바로 대표 장애처럼 기록하지 않는다
- 재시도 맥락이 드러난다

### 예시 3. validation 실패는 ERROR로 과장하지 않는다

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResult<Map<String, String>>> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
) {
    log.info("Rejected invalid request. requestPath={} method={} actorId={}",
            request.getRequestURI(),
            request.getMethod(),
            request.getAttribute("actorId"));

    Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toUnmodifiableMap(
                    FieldError::getField,
                    DefaultMessageSourceResolvable::getDefaultMessage,
                    (first, second) -> first
            ));

    return ResponseEntity.badRequest()
            .body(ApiResult.fail(ErrorCode.REQUEST_VALIDATION_FAILED, errors));
}
```

**좋은 이유:**

- 예상 가능한 4xx를 서버 장애처럼 기록하지 않는다
- 필요한 요청 맥락은 남긴다

### 예시 4. 메시지는 사건 설명 중심으로 쓴다

```java
log.error("Failed external auth request. provider={} requestPath={} actorId={} durationMs={}",
        provider, requestPath, actorId, durationMs, ex);
```

**좋은 이유:**

- 무엇이 실패했는지 바로 보인다
- 예외 메시지 품질에 로그 제목이 종속되지 않는다

## 나쁜 예시

### 예시 1. 같은 예외를 여러 레이어에서 반복 ERROR로 찍는다

```java
log.error("Client failed", ex);
log.error("Service failed", ex);
log.error("Controller failed", ex);
```

**나쁜 이유:**

- 한 실패가 여러 번 기록된다
- 검색/알림/집계 품질이 나빠진다

### 예시 2. 예외 메시지를 그대로 제목으로 쓴다

```java
log.error(ex.getMessage(), ex);
```

**나쁜 이유:**

- 사건 맥락이 없다
- 민감정보가 메시지에 섞일 수 있다
- 운영 키가 없다

### 예시 3. 요청 본문 전체를 예외 로그에 남긴다

```java
log.error("Failed create user request. requestBody={}", requestBody, ex);
```

**나쁜 이유:**

- PII/비밀번호/토큰이 유출될 수 있다
- payload 전문 로그는 기본 금지다

### 예시 4. 예상 가능한 business rejection을 ERROR로 남긴다

```java
log.error("Duplicate email sign-up attempt. email={}", request.email());
```

**나쁜 이유:**

- 서버 장애처럼 과장된다
- 개인식별정보 전체값이 그대로 남는다
- 운영 신호를 오염시킨다
