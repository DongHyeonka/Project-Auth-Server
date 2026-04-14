# Error Code / HTTP Status Separation 예시

## 좋은 예시

### 예시 1. ErrorCode가 status와 외부 메시지를 함께 관리한다

```java
public enum ErrorCode {
    REQUEST_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_FAILED", "Request validation failed"),
    MALFORMED_JSON_REQUEST(HttpStatus.BAD_REQUEST, "MALFORMED_JSON_REQUEST", "Malformed request body"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "Email already exists"),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "Invalid access token"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"),
    UPSTREAM_AUTH_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_AUTH_SERVER_UNAVAILABLE", "Authentication server is temporarily unavailable"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected server error");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
```

**좋은 이유:**

- HTTP status와 application code가 함께 정책화된다
- 문자열 하드코딩이 흩어지지 않는다
- 같은 code가 어디서든 같은 기본 status를 갖는다

### 예시 2. advice는 예외를 ErrorCode로 매핑하고, status와 body를 함께 만든다

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResult<Void>> handleDuplicateEmail() {
        ErrorCode errorCode = ErrorCode.DUPLICATE_EMAIL;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode));
    }

    @ExceptionHandler(InvalidAccessTokenException.class)
    public ResponseEntity<ApiResult<Void>> handleInvalidAccessToken() {
        ErrorCode errorCode = ErrorCode.INVALID_ACCESS_TOKEN;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleUserNotFound() {
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode));
    }
}
```

**좋은 이유:**

- status와 body code가 같은 정책 타입에서 나온다
- controller가 실패 응답을 직접 만들지 않는다
- ErrorCode와 HTTP status 역할이 모두 드러난다

### 예시 3. 같은 400 계열 아래 여러 세부 ErrorCode를 둔다

```java
@RestControllerAdvice
public class RequestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        FieldError::getField,
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (first, second) -> first
                ));

        ErrorCode errorCode = ErrorCode.REQUEST_VALIDATION_FAILED;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleMalformedJson() {
        ErrorCode errorCode = ErrorCode.MALFORMED_JSON_REQUEST;

        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResult.fail(errorCode));
    }
}
```

**좋은 이유:**

- 둘 다 400이지만 세부 원인은 ErrorCode로 구분된다
- status는 넓은 범주, code는 세부 식별자라는 역할 분리가 분명하다

## 나쁜 예시

### 예시 1. 모든 실패를 200으로 응답한다

```java
@ExceptionHandler(DuplicateEmailException.class)
public ResponseEntity<ApiResult<Void>> handleDuplicateEmail() {
    return ResponseEntity.ok(ApiResult.fail(ErrorCode.DUPLICATE_EMAIL));
}
```

**나쁜 이유:**

- body는 실패인데 HTTP status는 성공이다
- HTTP semantics와 application semantics가 충돌한다

### 예시 2. custom 6xx status를 사용한다

```java
@ExceptionHandler(UpstreamAuthServerUnavailableException.class)
public ResponseEntity<ApiResult<Void>> handleUpstreamFailure() {
    return ResponseEntity.status(601)
            .body(ApiResult.fail(ErrorCode.UPSTREAM_AUTH_SERVER_UNAVAILABLE));
}
```

**나쁜 이유:**

- 601은 유효한 HTTP status가 아니다
- 세부 원인 구분은 ErrorCode로 해야 한다

### 예시 3. @ResponseStatus(reason=...)를 REST API 기본 실패 전략으로 사용한다

```java
@ResponseStatus(code = HttpStatus.CONFLICT, reason = "Email already exists")
public class DuplicateEmailException extends RuntimeException {
}
```

**나쁜 이유:**

- HTTP status와 REST body 정책을 예외 클래스에 고정해 버린다
- reason 기반 sendError는 REST API 응답 규약과 잘 맞지 않는다
- body envelope 통일과 충돌하기 쉽다

### 예시 4. advice에서 문자열 코드와 예외 메시지를 직접 하드코딩한다

```java
@ExceptionHandler(DuplicateEmailException.class)
public ResponseEntity<ApiResult<Void>> handleDuplicateEmail(DuplicateEmailException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResult.fail("DUPLICATE_EMAIL", ex.getMessage()));
}
```

**나쁜 이유:**

- status/code/message 정책이 중앙화되지 않는다
- 외부 메시지와 내부 예외 메시지가 섞인다
- 다른 파일에서도 같은 문자열이 반복되기 쉽다
