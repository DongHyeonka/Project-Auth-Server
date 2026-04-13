# PII Masking 예시

## 좋은 예시

### 예시 1. 내부 식별자만 로그에 남긴다

```java
log.info("Completed password reset request. actorId={} requestPath={}",
        actorId, requestPath);
```

**좋은 이유:**

- 누구의 요청인지는 추적 가능하다
- 이메일/전화번호/비밀번호는 남기지 않는다

### 예시 2. 토큰은 일부만 식별 가능하게 남긴다

```java
String maskedToken = TokenMasker.prefix(token);
log.warn("Rejected external callback due to invalid token. provider={} tokenPrefix={}",
        provider, maskedToken);
```

**좋은 이유:**

- 토큰 전체 원문을 남기지 않는다
- 운영상 일부 식별은 가능하다

### 예시 3. 이메일은 부분 마스킹한다

```java
String maskedEmail = EmailMasker.mask(request.email());
log.info("Started email verification. actorId={} email={}",
        actorId, maskedEmail);
```

**좋은 이유:**

- 메일 발송 대상 추적은 가능하다
- 개인 식별정보 전체값을 남기지 않는다

### 예시 4. structured logging에도 안전한 필드만 넣는다

```java
MDC.put("actorId", actorId);
MDC.put("requestPath", request.getRequestURI());
```

**좋은 이유:**

- 운영 상관관계 필드는 남긴다
- token/session/password 같은 값은 MDC에 올리지 않는다

### 예시 5. 외부 오류 메시지는 정제해서 남긴다

```java
log.error("Failed external auth request. provider={} status={} errorCode={}",
        provider, status, "UPSTREAM_AUTH_SERVER_UNAVAILABLE", ex);
```

**좋은 이유:**

- 외부 시스템 에러 본문 원문을 그대로 노출하지 않는다
- 운영 키와 표준 에러 코드 중심으로 남긴다

## 나쁜 예시

### 예시 1. Authorization 헤더를 그대로 남긴다

```java
log.debug("Incoming request. authorization={}", request.getHeader("Authorization"));
```

**나쁜 이유:**

- access token 원문이 로그로 유출된다
- 디버그 로그라도 허용되지 않는다

### 예시 2. request body 전체를 남긴다

```java
log.info("Create user request body={}", requestBody);
```

**나쁜 이유:**

- 비밀번호, 이메일, 전화번호 등 민감값이 함께 들어갈 수 있다
- 운영 로그 노이즈도 크다

### 예시 3. 세션 ID를 그대로 남긴다

```java
log.warn("Invalid session. sessionId={}", sessionId);
```

**나쁜 이유:**

- 세션 식별값 원문이 노출된다
- OWASP도 세션 식별값은 직접 로그에 남기지 말라고 권고한다.

### 예시 4. 예외 메시지를 그대로 제목으로 쓴다

```java
log.error(ex.getMessage(), ex);
```

**나쁜 이유:**

- 예외 메시지 안 민감정보가 그대로 노출될 수 있다
- 사건 설명과 안전한 운영 키가 없다

### 예시 5. 외부 입력을 정제 없이 로그에 넣는다

```java
log.warn("Rejected request. keyword={}", request.getParameter("keyword"));
```

**나쁜 이유:**

- 줄바꿈/제어문자/악성 문자열이 로그 형식을 깨뜨릴 수 있다
- 민감 검색어가 그대로 남을 수 있다
