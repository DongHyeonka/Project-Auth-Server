# Log Message Format 기준

## 1. 목적

이 문서는 로그 메시지의 형식과 구성 원칙을 정의한다.

이 문서의 목표는 다음과 같다.

- 로그가 사람과 시스템 모두에게 읽기 쉬운 형식을 갖게 한다
- 검색, 집계, 상관 분석에 필요한 키를 일관되게 남긴다
- free text만으로 의미를 전달하는 로그를 줄인다
- 운영 로그와 디버깅 로그의 메시지 품질을 일정 수준 이상으로 유지한다

## 2. 근거 수준

- Official: Spring Boot 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 로그 메시지는 “짧은 사건 설명 + 핵심 키-값” 구조를 기본으로 한다

Spring Boot 기본 로그는 timestamp, level, thread, correlation ID, logger, message 같은 요소를 이미 분리해서 출력한다. 이 프로젝트에서는 message 본문도 이와 같은 방향으로 짧은 사건 설명 + 핵심 필드 구조를 따르도록 한다.

프로젝트 규칙:

- 메시지 앞부분은 한 문장 사건 설명
- 뒤에는 검색 가능한 핵심 필드를 key-value로 붙인다
- 긴 문장 서술형 로그보다 구조화된 짧은 메시지를 선호한다

권장 예:

```text
Failed to issue external auth token. provider=keycloak actorId=123 requestPath=/api/v1/sessions
```

### 3.2 free text보다 필드가 더 중요하다

Spring Boot는 구조화 로그를 공식 지원하고, JSON 필드 include/exclude/rename/add까지 제공한다. 이는 운영에서 로그를 사람이 읽기만 하는 것이 아니라 시스템이 수집·검색·집계한다는 뜻이다.

프로젝트 규칙:

- 메시지에 사건 설명은 필요하지만, 분석용 핵심 정보는 필드 형태로 남긴다
- 같은 종류의 로그는 가능한 한 같은 키 이름을 쓴다
- 검색/집계 가능한 키를 free text 속에만 숨기지 않는다

### 3.3 로그 형식은 전역적으로 일관되어야 한다

Spring Boot는 기본 포맷과 구조화 포맷을 전역 설정으로 관리할 수 있다. 이 프로젝트도 메시지 형식을 클래스마다 제각각 두지 않는다.

프로젝트 규칙:

- 같은 종류의 사건은 같은 키 이름과 같은 순서를 최대한 유지한다
- 운영 로그 형식은 팀 공통 규약으로 다룬다
- 특정 개발자 취향에 따라 메시지 문체가 바뀌지 않게 한다

## 4. 기본 메시지 형식

### 4.1 권장 기본 형식

권장 형식:

```text
<짧은 사건 설명>. key1=value1 key2=value2 key3=value3
```

프로젝트 규칙:

- 사건 설명은 과도하게 길지 않게 쓴다
- 사건 설명 뒤에 핵심 필드를 공백으로 구분해 이어 붙인다
- 문장 속에 값을 길게 섞어 넣기보다 key=value 형태를 우선한다

예:

```text
User registration completed. actorId=123 userId=u_001
External auth request failed. provider=keycloak actorId=123 status=503
```

### 4.2 메시지는 과거형/완료형보다 사건 중심으로 쓴다

프로젝트 규칙:

- “무슨 일이 일어났는가”가 바로 보이게 쓴다
- 장황한 설명보다 사건명 중심으로 쓴다
- 성공/실패/재시도/대체 경로가 제목 수준에서 드러나야 한다

권장:

- Created session
- Failed to create session
- Retried external auth request
- Applied fallback token validation

비권장:

- Trying to do session creation and got an unexpected issue while processing

## 5. 필수/권장 필드 규칙

### 5.1 운영 핵심 로그의 필수 후보 필드

Spring Boot 기본 로그에는 이미 thread, logger, correlation ID 같은 정보가 들어갈 수 있고, tracing이 활성화되면 correlation ID도 로그에 포함된다. 이 프로젝트는 메시지 본문에서도 운영에 필요한 business key를 추가로 남긴다.

운영 핵심 로그의 권장 필드:

- traceId 또는 correlation ID 연결 가능 정보
- actorId 또는 principal 식별자
- requestPath
- operation
- resourceId
- externalSystem
- status 또는 errorCode
- durationMs

프로젝트 규칙:

- 모든 로그에 다 넣으라는 뜻은 아니다
- 그 사건을 운영에서 추적하는 데 필요한 최소 필드를 고른다
- 메시지마다 필드 이름을 바꾸지 않는다

### 5.2 같은 의미에는 같은 키 이름을 쓴다

프로젝트 규칙:

- 사용자 식별자는 userId 또는 actorId 중 하나로 표준화한다
- 경로는 path가 아니라 requestPath처럼 의미를 분명히 한다
- 외부 연동 대상은 provider, externalSystem, clientName 중 문서로 정한 하나를 사용한다

예:

- userId, uid, memberId를 섞지 않는다
- url, uri, path를 상황마다 바꾸지 않는다

## 6. 사람이 읽는 로그와 구조화 로그의 관계

### 6.1 기본 텍스트 로그도 구조화 가능해야 한다

Spring Boot는 콘솔/파일 로그를 기본 텍스트 형식으로 출력하면서도, correlation ID와 핵심 정보를 포함할 수 있게 설계되어 있다.

프로젝트 규칙:

- 텍스트 로그라도 key=value 패턴을 유지한다
- grep/search가 가능해야 한다
- “말이 되는 문장”보다 “검색 가능한 문장”을 우선한다

### 6.2 구조화 로그(JSON)는 수집 시스템이 있으면 우선 검토한다

Spring Boot는 ECS, GELF, Logstash JSON structured logging을 공식 지원한다.

프로젝트 규칙:

- 중앙 수집 시스템(예: ELK, Graylog, Datadog 등)이 있으면 structured logging을 우선 검토한다
- 다만 JSON 로그를 쓰더라도 필드 naming 규칙과 메시지 사건 설명 규칙은 그대로 유지한다
- 텍스트 로그와 JSON 로그가 서로 전혀 다른 의미 체계를 가지지 않게 한다

### 6.3 JSON 구조는 ingestion 시스템에 맞추되, 프로젝트 핵심 필드는 유지한다

Spring Boot는 JSON structured logging에서 include/exclude/rename/add를 지원한다.

프로젝트 규칙:

- 수집 시스템 요구에 맞게 JSON 필드명을 조정할 수 있다
- 그러나 프로젝트 핵심 검색 키(traceId, actorId, requestPath, errorCode)는 일관되게 유지한다
- ingestion 편의 때문에 business 의미가 흐려지지 않게 한다

## 7. 예외와 스택트레이스 메시지 규칙

### 7.1 메시지와 예외 스택트레이스는 역할이 다르다

Spring Boot는 구조화 로그에서 예외가 함께 로그되면 stack trace도 포함되며, 비용을 줄이기 위해 출력 방식을 조정할 수 있다고 설명한다.

프로젝트 규칙:

- 메시지는 “무슨 요청/작업이 왜 실패했는지”를 요약한다
- stack trace는 원인 분석용이다
- stack trace가 있으니 메시지를 대충 쓰지 않는다
- 반대로 메시지가 충분하다고 stack trace를 무조건 생략하지도 않는다

### 7.2 예외 메시지를 그대로 로그 제목으로 쓰지 않는다

프로젝트 규칙:

- log.error(ex.getMessage(), ex) 형태를 기본값으로 쓰지 않는다
- 사건 설명과 운영 키를 먼저 쓰고 예외를 마지막 인자로 붙인다
- 예외 메시지는 보조 정보이지, 로그 제목의 전부가 아니다

권장:

```java
log.error("Failed to issue token. provider={} actorId={}", provider, actorId, ex);
```

비권장:

```java
log.error(ex.getMessage(), ex);
```

## 8. 민감정보/대용량 데이터 규칙

### 8.1 메시지 본문에 민감정보 원문을 넣지 않는다

프로젝트 규칙:

- 비밀번호, 액세스 토큰, 리프레시 토큰, 인증 헤더, 주민번호, 카드번호, 이메일 전체값 등은 원문 출력 금지
- 필요한 경우 일부 마스킹, 해시, 길이/유형 정보만 출력
- 구조화 로그에서도 같은 기준을 적용한다

### 8.2 payload 전문을 기본 로그 메시지에 넣지 않는다

프로젝트 규칙:

- request/response 전문 출력은 기본 금지
- 꼭 필요하면 별도 debug/trace 전용 경로에서 제한적으로 출력
- 긴 배열, JSON 본문, 바이너리 응답, HTML 전체를 로그 메시지에 직접 넣지 않는다

### 8.3 식별자는 최소한으로 남긴다

프로젝트 규칙:

- 사람을 직접 식별하는 값보다 내부 식별자(userId, sessionId, orderId)를 우선 사용
- 외부 식별자가 필요해도 전체 원문 대신 일부만 남기는 방식을 검토한다

## 9. 메시지 작성 세부 규칙

### 9.1 시제와 문체를 통일한다

프로젝트 규칙:

- 사건 제목은 영어 기준 단순 과거/완료형 또는 failed/succeeded 형식으로 통일한다
- 같은 팀 안에서 create user success, user created, successfully created user처럼 문체가 섞이지 않게 한다
- 문장 종결 부호는 짧게 유지한다

권장 예:

- Created user
- Failed to create user
- Completed session cleanup
- Rejected invalid request

### 9.2 불필요한 수식어를 줄인다

프로젝트 규칙:

- very, really, unexpectedly, seriously 같은 감정/강조 표현 지양
- 심각도는 레벨과 에러 코드가 표현하게 한다
- 메시지는 사실 중심으로 작성한다

### 9.3 단위가 있는 값은 키 이름에 단위를 포함한다

프로젝트 규칙:

- 시간은 durationMs
- 바이트는 payloadBytes
- 개수는 itemCount
- 단위를 메시지 문장 속에 숨기지 않는다

## 10. 권장 메시지 패턴

### 10.1 요청 처리

성공:

```text
Completed request. requestPath=/api/v1/users method=POST status=201 durationMs=42
```

실패:

```text
Failed request. requestPath=/api/v1/users method=POST status=500 errorCode=INTERNAL_SERVER_ERROR durationMs=42
```

### 10.2 외부 연동

성공:

```text
Completed external auth request. provider=keycloak status=200 durationMs=84
```

재시도 후 성공:

```text
Succeeded external auth request after retry. provider=keycloak attempts=2 durationMs=312
```

실패:

```text
Failed external auth request. provider=keycloak status=503 errorCode=UPSTREAM_AUTH_SERVER_UNAVAILABLE
```

### 10.3 배치/스케줄

시작:

```text
Started expired session cleanup. job=expired-session-cleanup
```

완료:

```text
Completed expired session cleanup. job=expired-session-cleanup deletedCount=143 durationMs=1820
```

실패:

```text
Failed expired session cleanup. job=expired-session-cleanup durationMs=905
```

## 11. 구현 규칙

### 11.1 logger name에 의미를 실지 말고 메시지에 실는다

Spring Boot 기본 포맷은 logger name을 별도로 출력한다.

프로젝트 규칙:

- logger name은 클래스/패키지 출처를 나타내는 데 충분하다
- 사건 의미는 메시지와 key-value 필드에 둔다
- logger 이름 자체를 읽어야만 사건을 이해할 수 있게 만들지 않는다

### 11.2 MDC/trace와 겹치는 필드는 중복을 줄인다

Spring Boot tracing은 correlation ID를 로그에 포함할 수 있고, 패턴 커스터마이징도 지원한다.

프로젝트 규칙:

- 이미 공통 패턴에 있는 값(traceId 등)을 메시지에 또 반복하지 않는다
- 다만 수집 시스템이나 검색 UX상 필요한 경우만 선택적으로 중복한다
- 메시지 필드와 로그 패턴 필드의 책임을 나눈다

## 12. 금지 규칙

다음은 기본 금지다.

- free text만 길게 쓰고 검색 가능한 키를 남기지 않음
- 예외 메시지 자체를 로그 제목으로 사용
- 같은 의미의 키 이름을 로그마다 다르게 사용
- request/response payload 전문을 기본 로그에 출력
- 민감정보 원문 출력
- structured logging을 쓰면서도 JSON 필드 의미가 일관되지 않음
- traceId, actorId, requestPath 같은 핵심 필드를 상황마다 제멋대로 이름 붙임

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 메시지는 한 줄 사건 설명으로 빠르게 이해 가능한가?
- 운영에 필요한 핵심 값이 key=value 형태로 드러나는가?
- 같은 종류의 로그와 키 이름/순서/문체가 일관적인가?
- 메시지와 stack trace의 역할이 분리되어 있는가?
- 민감정보나 대용량 payload가 원문으로 들어가지 않았는가?
- structured logging으로 전환해도 의미가 유지되는 형식인가?
