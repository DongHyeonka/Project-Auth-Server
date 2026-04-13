# Exception Log 기준

## 1. 목적

이 문서는 예외를 로그로 남길 때의 기준을 정의한다.

이 문서의 목표는 다음과 같다.

- 같은 실패를 여러 레이어에서 중복 로그하는 일을 줄인다
- 대표 예외 로그가 운영에 필요한 맥락을 충분히 담게 한다
- 예외 메시지, stack trace, 민감정보 노출을 통제한다
- 예외 처리와 예외 로깅의 책임을 분리한다

## 2. 근거 수준

- Official: Spring Framework / Spring Boot / OWASP 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 예외 처리와 예외 로깅은 같은 문제가 아니다

Spring은 @ExceptionHandler, @ControllerAdvice, @RestControllerAdvice, ResponseEntityExceptionHandler로 예외를 HTTP 응답으로 변환할 수 있게 합니다. 하지만 예외를 응답으로 바꾸는 위치가 곧 예외를 항상 거기서만 로그해야 한다는 뜻은 아닙니다. 이 프로젝트에서는 예외 변환 책임과 대표 예외 로그 책임을 구분합니다.

프로젝트 규칙:

- 예외를 HTTP/API 응답으로 만드는 책임과
- 예외를 운영 로그로 남기는 책임을
- 같은 지점에 둘 수도 있지만, 개념적으로는 구분한다

### 3.2 대표 예외 로그는 한 번만 남긴다

Spring은 예외를 전역 advice에서 일관되게 처리할 수 있게 해 주므로, 같은 예외가 controller, service, client, advice에서 모두 stack trace와 함께 반복 기록될 필요는 없습니다. 실무적으로도 한 실패에 대해 대표 ERROR 로그 한 번을 남기고, 나머지는 보조 맥락만 남기는 편이 검색·알림·분석 품질이 좋습니다.

프로젝트 규칙:

- 한 실패에 대해 대표 예외 로그 한 번을 원칙으로 한다
- 하위 계층은 필요하면 DEBUG 또는 WARN으로 맥락만 남긴다
- 같은 stack trace를 여러 레이어에서 반복 ERROR로 남기지 않는다

### 3.3 예외 로그는 “무슨 일이 왜 어디서 실패했는지”를 설명해야 한다

Spring Boot는 기본 로그에 level, thread, logger, correlation 정보를 담을 수 있고, structured logging도 지원합니다. 따라서 예외 로그도 단순 ex.getMessage()가 아니라, 요청/작업/외부 시스템/소요시간/에러 코드 같은 운영 키를 함께 남겨야 의미가 있습니다.

프로젝트 규칙:

- 예외 로그는 사건 설명을 먼저 쓴다
- 그 뒤에 운영 필드(key=value)를 붙인다
- 예외 객체(stack trace)는 마지막 인자로 넘긴다

권장 예:

```text
Failed external auth request. provider=keycloak actorId=u_001 requestPath=/api/v1/sessions errorCode=UPSTREAM_AUTH_SERVER_UNAVAILABLE
```

## 4. 대표 로그 위치 규칙

### 4.1 HTTP 요청 실패의 대표 로그는 공통 경계에서 남긴다

Spring MVC는 전역 @ControllerAdvice/@RestControllerAdvice에서 controller 예외를 공통 처리할 수 있습니다. 따라서 일반 HTTP 요청 실패의 대표 예외 로그는 보통 전역 예외 처리 경계 또는 요청 완료 공통 로깅 경계 중 한 곳에서 일관되게 남기는 것이 적절합니다.

프로젝트 규칙:

- 일반 요청 실패는 공통 advice 또는 공통 요청 로깅 경계에서 대표 로그를 남긴다
- controller 메서드마다 try-catch + log.error를 반복하지 않는다
- controller local @ExceptionHandler가 있어도 대표 예외 로그 위치는 프로젝트 단위로 일관되게 유지한다

### 4.2 외부 API 실패의 대표 로그는 “최종 실패가 확정된 경계”에서 남긴다

외부 연동은 client/adapter 계층에서 많은 중간 실패가 생길 수 있습니다. 이런 중간 실패를 모두 ERROR로 남기면 재시도 후 성공한 케이스도 장애처럼 보일 수 있습니다. 따라서 대표 로그는 최종적으로 호출 결과가 실패로 확정된 시점에 남기는 것이 좋습니다. 이 원칙은 앞서 정한 log level 기준과도 맞습니다.

프로젝트 규칙:

- 재시도 전 단일 실패는 기본적으로 DEBUG 또는 WARN
- 재시도 후 최종 실패가 되면 대표 ERROR
- fallback으로 정상 복구되면 WARN 또는 INFO로 남기고 ERROR로 과장하지 않는다

### 4.3 배치/스케줄/비동기 작업은 작업 경계에서 대표 로그를 남긴다

Spring의 예외 처리 문맥은 HTTP controller만을 위한 것이 아니므로, 스케줄/비동기/배치 작업에서는 작업 진입점이나 orchestration 경계에서 대표 예외 로그를 남겨야 합니다.

프로젝트 규칙:

- 스케줄 작업은 job/unit-of-work 경계에서 대표 로그를 남긴다
- 비동기 후속 작업도 작업 단위 식별자와 함께 실패를 기록한다
- 내부 helper 메서드들이 모두 각자 ERROR를 찍지 않는다

## 5. 로그 레벨 규칙

### 5.1 최종 실패 예외는 ERROR

프로젝트 규칙:

- 요청/작업이 최종 실패로 끝났으면 ERROR
- 응답이 5xx이거나, 작업 결과가 실패로 종료되면 ERROR
- 복구되지 않은 예외는 ERROR

### 5.2 복구된 예외는 WARN 또는 DEBUG

프로젝트 규칙:

- 재시도 후 성공
- fallback 후 성공
- 대체 경로로 정상 처리

이 경우 대표 로그는 WARN 또는 필요 시 INFO

stack trace가 꼭 필요하지 않으면 DEBUG/WARN 요약 로그만 남긴다

### 5.3 예상 가능한 클라이언트 오류는 무조건 ERROR로 남기지 않는다

Spring에서 validation 예외나 request parsing 예외도 전역 advice에서 처리할 수 있지만, 그것이 모두 서버 이상을 뜻하는 것은 아닙니다. OWASP도 보안상 가치 있는 실패는 남기라고 하지만, 민감정보 노출 없이 맥락 중심으로 남기라고 권고합니다.

프로젝트 규칙:

- validation 실패
- 잘못된 요청 파라미터
- business rule rejection
- 권한 없음

같은 예상 가능한 4xx는 기본적으로 INFO 또는 WARN

대량 이상 징후가 아니면 ERROR로 과장하지 않는다

## 6. 메시지 구성 규칙

### 6.1 예외 로그 제목은 예외 메시지가 아니라 사건 설명이다

프로젝트 규칙:

- log.error(ex.getMessage(), ex)를 기본 금지
- 로그 제목은 애플리케이션이 통제하는 사건 설명으로 쓴다
- 예외 메시지는 보조 정보일 뿐, 로그 제목의 전부가 아니다

권장:

```java
log.error("Failed to create session. actorId={} requestPath={} errorCode={}",
        actorId, requestPath, errorCode, ex);
```

### 6.2 대표 예외 로그의 권장 필드

권장 필드:

- traceId 또는 correlation ID 연결 가능 정보
- requestPath
- method
- actorId
- operation
- resourceId
- externalSystem
- errorCode
- status
- durationMs

프로젝트 규칙:

- 모든 필드를 다 강제하지는 않는다
- 해당 실패를 운영에서 추적하는 데 필요한 최소 필드를 남긴다
- 같은 종류의 예외 로그는 같은 필드 이름을 유지한다

### 6.3 stack trace만으로 맥락을 대체하지 않는다

Spring Boot는 structured logging에서 stack trace 출력 방식도 조정할 수 있지만, stack trace는 어디까지나 원인 분석용입니다. 운영자가 “무슨 요청/작업이 왜 실패했는지”를 빠르게 이해하려면 메시지 맥락이 필요합니다.

프로젝트 규칙:

- stack trace가 있으니 메시지를 대충 쓰지 않는다
- 메시지는 사건 설명과 운영 키를 담고
- stack trace는 원인 분석을 보조한다

## 7. stack trace 규칙

### 7.1 대표 ERROR 로그에는 기본적으로 stack trace를 포함한다

프로젝트 규칙:

- 최종 실패를 나타내는 대표 ERROR 로그는 기본적으로 예외 객체를 함께 남긴다
- stack trace 없는 ERROR 로그는 원인 분석에 불리하므로 예외적 경우에만 허용한다

### 7.2 WARN/INFO에서는 stack trace를 신중하게 남긴다

프로젝트 규칙:

- 재시도 후 성공, fallback 후 성공 같은 경우에는 stack trace 없이 요약 로그를 우선한다
- 같은 원인의 경고가 고빈도로 반복될 수 있으면 stack trace를 매번 남기지 않는다
- 필요하면 최초 1회만 stack trace, 이후는 요약만 남기는 전략을 검토한다

### 7.3 너무 큰 stack trace는 구조화 포맷과 수집 비용을 고려한다

Spring Boot는 structured logging에서 stack trace 포함과 길이, 출력 방식을 조정할 수 있습니다. 큰 예외가 자주 발생하는 시스템에서는 수집 비용과 검색성을 고려해야 합니다.

프로젝트 규칙:

- 대량 반복 예외의 stack trace 출력 정책은 운영 비용을 고려해 조정한다
- 하지만 비용을 이유로 대표 실패의 원인 정보가 완전히 사라지게 만들지는 않는다

## 8. 민감정보 규칙

### 8.1 예외 로그도 PII/sensitive 규칙을 그대로 따른다

OWASP는 세션 식별값, 토큰, 비밀번호, 민감 PII, 키/비밀값 등은 직접 로그에 남기지 말라고 권고합니다. 예외 로그도 예외가 아닙니다.

프로젝트 규칙:

- 예외 로그 제목에 민감정보 원문 금지
- 예외 메시지에 민감정보가 포함될 수 있으면 그대로 재사용 금지
- request/response body 전문을 예외 맥락으로 붙이지 않는다
- 외부 시스템 에러 본문도 원문 그대로 남기지 않는다

### 8.2 stack trace에도 비밀값이 섞일 수 있음을 전제한다

프로젝트 규칙:

- 예외 생성 메시지에 비밀값을 넣지 않는 것이 우선
- 예외에 포함된 URL, 헤더, payload, connection string, token 등을 주의한다
- 비밀값이 exception message에 들어가도록 코드를 짜지 않는다

## 9. 번역(translation) 규칙

### 9.1 내부 예외와 외부 응답 메시지를 분리한다

Spring은 @ExceptionHandler와 ResponseEntityExceptionHandler로 응답 변환을 지원합니다. 이 프로젝트는 예외 로그와 API 응답 메시지도 분리합니다. 즉, 로그에는 운영에 필요한 안전한 맥락을 남기고, 응답은 ErrorCode와 외부 메시지 규약으로 보냅니다.

프로젝트 규칙:

- 로그 메시지 ≠ API 응답 메시지
- ex.getMessage()를 API 응답에도, 로그 제목에도 그대로 재사용하지 않는다
- advice는 응답 변환을, 대표 로그는 운영 맥락 기록을 담당한다

### 9.2 예외 번역 계층이 있다면 원인 체인을 잃지 않는다

프로젝트 규칙:

- external/client 예외 → integration 예외 → application 예외로 번역할 수 있다
- 이 과정에서 root cause를 완전히 잃지 않는다
- 대표 로그는 번역된 비즈니스 의미와 원인 예외를 함께 남길 수 있어야 한다

## 10. 위치별 세부 규칙

### 10.1 controller

프로젝트 규칙:

- controller에서 try-catch + log.error를 기본 금지
- 공통 advice가 있는 구조에서는 controller는 예외를 그대로 위로 전파한다
- endpoint-local 특별 정책이 있어도 대표 예외 로그는 한 번만 남긴다

### 10.2 application service

프로젝트 규칙:

- application은 예외를 business/application 의미로 번역할 수 있다
- 하지만 같은 예외를 무조건 ERROR로 남기지는 않는다
- 최종 실패 책임이 상위 경계에 있으면 여기서는 DEBUG/WARN 맥락만 남길 수 있다

### 10.3 external client / integration adapter

프로젝트 규칙:

- 외부 호출 1회 실패는 기본적으로 요약 로그
- 재시도/fallback/최종 실패 여부에 따라 상위에서 대표 로그를 결정한다
- 외부 payload/headers/token 원문은 남기지 않는다

### 10.4 advice / global exception handler

프로젝트 규칙:

- 요청 실패의 대표 로그 위치를 advice로 정했다면 거기서 일관되게 남긴다
- validation/4xx/5xx에 따라 레벨을 다르게 적용할 수 있다
- advice가 응답 생성만 하고 로그는 요청 공통 경계에서 남기는 구조도 허용하되, 프로젝트 전체로 하나를 택한다

## 11. 금지 규칙

다음은 기본 금지다.

- 같은 예외를 여러 레이어에서 모두 ERROR로 기록
- log.error(ex.getMessage(), ex) 남발
- request/response body 전문을 예외 로그에 포함
- 토큰, 세션 ID, 비밀번호, 키, PII를 예외 로그에 원문으로 기록
- validation/예상 가능한 4xx를 무조건 ERROR 처리
- controller마다 try-catch + log.error + ResponseEntity 반복
- stack trace 없이 맥락도 없는 ERROR 한 줄만 남김

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 실패에 대해 대표 예외 로그가 한 번만 남는가?
- 대표 로그는 사건 설명과 운영 키를 담는가?
- 최종 실패만 ERROR로 남기고 있는가?
- 복구된 예외를 과도하게 ERROR로 찍지 않는가?
- 예외 로그에도 민감정보 마스킹 규칙이 그대로 적용되는가?
- 로그 메시지와 API 응답 메시지를 분리하고 있는가?
- stack trace가 필요한 곳에는 남고, 불필요한 중복은 줄였는가?
