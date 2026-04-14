# Trace / Principal / Path Recording 기준

## 1. 목적

이 문서는 운영 로그와 트레이싱에서 traceId, principal(현재 사용자/호출 주체), requestPath를 어디서, 어떤 이름으로, 어느 정도까지 기록할지 정의한다.

이 문서의 목표는 다음과 같다.

- 한 요청/작업을 trace 단위로 추적 가능하게 만든다
- 사용자/호출 주체와 요청 경로를 일관된 키로 검색 가능하게 만든다
- Security 타입과 Servlet 저수준 접근이 여러 계층으로 퍼지는 것을 막는다
- 민감정보를 보호하면서도 운영에 필요한 상관관계를 유지한다

## 2. 근거 수준

- Official: Spring Boot / Spring Framework / Spring Security 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 traceId, principal, requestPath는 운영 상관관계의 핵심 키다

Spring Boot는 tracing이 켜져 있으면 로그에 correlation ID를 기본 포함할 수 있다고 설명한다. 이 프로젝트에서는 그 위에 누가(actor/principal), 어떤 경로(requestPath) 에서 발생한 사건인지까지 함께 남겨서, 장애 분석과 감사 추적이 가능한 최소 세트를 만든다.

프로젝트 규칙:

- 대표 운영 로그는 가능한 한 traceId, principal/actor, requestPath 중 필요한 값을 함께 가진다
- 세 값은 “있으면 좋은 정보”가 아니라, 요청/작업 상관 분석의 기본 키로 본다
- 다만 모든 로그에 세 값을 기계적으로 다 넣는 것은 지양하고, 사건 종류에 맞게 최소 세트를 고른다

### 3.2 공통 상관 키는 로그 패턴/MDC에, 비즈니스 식별자는 메시지 필드에 둔다

Spring Boot는 correlation ID를 로그 패턴에 포함시키고, 형식도 조정할 수 있게 한다. 따라서 trace/correlation 같은 공통 값은 패턴/MDC 레벨에서 처리하고, principal, requestPath, operation, resourceId 같은 값은 로그 메시지 필드로 남기는 것이 자연스럽다.

프로젝트 규칙:

- traceId는 기본적으로 로그 패턴/MDC에 두는 것을 우선
- principal, requestPath, operation, resourceId는 메시지 key-value 필드로 남긴다
- 이미 패턴에 있는 값을 메시지에 불필요하게 중복하지 않는다

### 3.3 principal 기록은 인증 객체 접근 방식과 분리해서 생각하지 않는다

Spring Security는 @AuthenticationPrincipal과 메타 애노테이션 기반 @CurrentUser 패턴을 제공한다. 이 프로젝트에서는 현재 사용자 접근 방식과 principal 로깅 규칙을 같은 방향으로 맞춘다. 즉, controller가 SecurityContextHolder를 직접 뒤져서 principal을 로그에 넣는 식의 접근을 기본 금지한다.

프로젝트 규칙:

- principal 기록은 @CurrentUser 같은 전용 현재 사용자 접근 규칙과 함께 설계한다
- 보안 프레임워크 내부 타입 전체를 로그에 덤프하지 않는다
- 로그에는 필요한 최소 principal 식별자만 남긴다

## 4. TraceId 기록 규칙

### 4.1 traceId는 tracing이 활성화된 서비스에서 기본적으로 로그에 포함되어야 한다

Spring Boot는 Micrometer Tracing을 사용하는 경우 로그에 correlation ID를 기본 포함할 수 있다고 설명한다. 기본 correlation ID는 traceId-spanId 형태다.

프로젝트 규칙:

- tracing이 켜진 서비스는 운영 로그에 trace/correlation 식별자가 기본 포함되어야 한다
- traceId는 가능한 한 로그 패턴/MDC 레벨에서 일관되게 출력한다
- 서비스마다 trace 키 이름이나 형식을 제각각 바꾸지 않는다

### 4.2 traceId는 대표 로그 검색의 1차 키로 본다

프로젝트 규칙:

- 요청 실패, 외부 연동 실패, 배치 실패 같은 대표 로그는 trace로 묶여야 한다
- 한 요청 흐름에서 여러 로그가 흩어져도 traceId로 묶어 검색 가능해야 한다
- traceId가 없다면 같은 요청의 controller/client/db 연관 로그를 잇기 어렵다는 점을 기본 전제로 둔다

### 4.3 비동기/스케줄/외부 연동 로그도 가능한 한 trace 연결성을 유지한다

Spring Boot observability는 logging, metrics, traces를 함께 다루고, tracing은 서비스 내부/외부 경계를 따라 상관관계를 유지하는 데 쓰인다. 이 프로젝트에서는 비동기 작업이나 외부 연동도 가능한 한 원 요청과 연결 가능한 trace 문맥을 유지하는 방향을 기본 권장으로 둔다.

프로젝트 규칙:

- 동기 요청 경로에서 시작한 비동기 후속 작업은 가능한 경우 trace 연결성을 유지한다
- 스케줄/배치처럼 원 요청이 없는 작업은 자체 trace/correlation을 생성해 대표 로그를 남긴다
- 외부 시스템 호출 로그도 내부 요청 trace와 연계되는 쪽을 우선한다

## 5. Principal 기록 규칙

### 5.1 principal은 “누가 호출했는가”를 식별하는 최소 값만 남긴다

Spring MVC는 Principal을 메서드 인자로 지원하고, Spring Security는 @AuthenticationPrincipal로 principal을 직접 주입할 수 있다. 하지만 이 프로젝트에서 로그에 남길 principal은 Authentication 전체나 raw claim map이 아니라, 운영에 필요한 최소 식별자다.

프로젝트 규칙:

- 기본 로그 principal 필드명은 actorId 또는 principalId 중 하나로 통일한다
- 권장 기본값은 actorId
- principal 전체 객체, authorities 전체, credentials, token 원문은 로그에 남기지 않는다

### 5.2 인증된 사용자가 없으면 그 상태도 일관되게 표현한다

프로젝트 규칙:

- 비로그인 요청, 공개 endpoint, 시스템 내부 작업은 principal 부재를 일관되게 표현한다
- 예:
- actorId=anonymous
- actorId=system
- actorId=batch
- null, empty string, guest, unknownUser 같은 값을 서비스마다 섞지 않는다

### 5.3 principal은 사람이 직접 식별되는 값보다 내부 식별자를 우선한다

프로젝트 규칙:

- 이메일, 전화번호, 로그인 이름 전체값보다 내부 userId/subjectId를 우선 기록한다
- 외부 노출 식별자가 꼭 필요해도 전체 원문 대신 최소한으로 남긴다
- principal 로깅이 곧 PII 노출로 이어지지 않게 한다

### 5.4 principal 로깅은 controller/web 경계에서 필요한 값을 추출해 전달한다

Spring Security는 @AuthenticationPrincipal과 메타 애노테이션 기반 접근을 제공하므로, controller/web 경계에서 전용 현재 사용자 타입 또는 필요한 필드만 받을 수 있다.

프로젝트 규칙:

- controller는 @CurrentUser 또는 @CurrentUserId 같은 방식으로 actor 정보를 얻는다
- service/domain/util에서 SecurityContextHolder를 다시 조회하지 않는다
- 로그에 principal이 필요하면 web 경계나 공통 interceptor/filter가 이를 정규화해서 넣는다

## 6. RequestPath 기록 규칙

### 6.1 requestPath는 대표 HTTP 로그의 기본 필드다

Spring Framework는 request logging filter 계열에서 request URI와 필요 시 query string도 로그에 넣을 수 있게 제공한다. 또한 OncePerRequestFilter는 요청 시작 시 1회 실행을 기본으로 한다.

프로젝트 규칙:

- 대표 HTTP 로그에는 requestPath를 기본 필드로 둔다
- 필드명은 requestPath로 통일한다
- path, uri, requestUri, url을 혼용하지 않는다

### 6.2 기본값은 path만 기록하고, query string은 선택적으로 다룬다

AbstractRequestLoggingFilter 계열은 query string을 선택적으로 포함할 수 있다. 이는 query string이 항상 로그에 적합한 것은 아니라는 뜻이기도 하다.

프로젝트 규칙:

- 기본 기록 대상은 path만
- query string은 기본적으로 로그에 포함하지 않는다
- query string이 운영상 꼭 필요하면 별도 마스킹/allowlist 기준 아래 제한적으로 기록한다

### 6.3 경로는 템플릿이 아니라 실제 요청 path를 기본으로 한다

프로젝트 규칙:

- 기본 requestPath는 실제 요청된 path
- 예: /api/v1/users/123
- 다만 집계/카디널리티 문제가 크면 별도 필드로 route template를 함께 관리할 수 있다
- 예:
- requestPath=/api/v1/users/123
- route=/api/v1/users/{userId}

이 항목은 실무 운영 편의를 위한 Practice + Project Recommendation 이다.

## 7. 기록 위치 규칙

### 7.1 공통 HTTP 기록은 filter 또는 interceptor에서 수행할 수 있다

Spring Framework는 OncePerRequestFilter를 통해 요청 시작 시 1회 실행되는 filter를 만들 수 있고, request logging filter 계열도 제공한다. 또한 Spring MVC interceptor는 handler 전후 공통 처리에 쓰인다.

프로젝트 규칙:

- traceId, requestPath, 시작 시각, 응답 상태 같은 공통 HTTP 기록은 filter 또는 interceptor에서 공통 처리할 수 있다
- 인증 완료 이후 principal까지 같이 기록해야 하면 보안 필터 체인 이후 시점을 고려한다
- request/response wrapping 같은 HTTP concern은 filter, handler 전후 메타데이터는 interceptor 쪽을 우선 검토한다

### 7.2 대표 요청 로그는 한 곳에서 남긴다

프로젝트 규칙:

- 요청 시작/완료/실패 대표 로그는 공통 컴포넌트 한 곳에서 남긴다
- controller마다 요청 진입/종료 로그를 반복 작성하지 않는다
- 공통 요청 로그와 개별 비즈니스 로그를 구분한다

### 7.3 principal이 결정되기 전/후를 구분한다

Spring Security 문서는 커스텀 필터가 현재 사용자를 알아야 한다면 인증 필터 뒤에 배치해야 함을 설명한다.

프로젝트 규칙:

- 요청 시작 시점에는 principal이 아직 없을 수 있다
- principal까지 포함한 대표 로그가 필요하면 인증 이후 시점에서 기록한다
- “pre-auth request log”와 “authenticated request log”를 같은 규칙 없이 섞지 않는다

## 8. MDC / Structured Logging 규칙

### 8.1 traceId는 MDC/패턴에, actorId/requestPath는 필요 시 MDC 또는 구조화 필드에 둔다

Spring Boot는 correlation ID를 로그 패턴에 포함하고, 구조화 로그도 지원한다.

프로젝트 규칙:

- traceId는 패턴/MDC 기본값으로 두는 것을 우선
- actorId, requestPath는
- 메시지 key-value
- MDC
- structured JSON field
- 중 하나로 일관되게 선택한다
- 같은 서비스 안에서 세 방식을 뒤섞지 않는다

### 8.2 MDC를 쓴다면 누수 없이 정리한다

프로젝트 규칙:

- 요청 단위 MDC 값은 요청 종료 시 반드시 정리한다
- async 경계가 있으면 MDC 전파/정리 전략을 별도 검토한다
- 이전 요청의 principal/path가 다음 로그에 새어 나가지 않게 한다

## 9. 민감정보/카디널리티 규칙

### 9.1 principal과 path는 유용하지만 무제한으로 남기지 않는다

프로젝트 규칙:

- principal은 내부 식별자 중심
- path는 기본 path만
- query string, 전체 URL, raw header, token, cookie는 기본 금지
- traceId는 민감정보가 아니더라도 외부 공개 출력 정책은 별도 검토한다

### 9.2 고카디널리티 값을 메트릭 태그처럼 남발하지 않는다

프로젝트 규칙:

- 로그 본문에는 requestPath=/api/v1/users/123 같은 고유 path가 들어갈 수 있다
- 하지만 메트릭 태그/라벨에는 그대로 쓰지 않고 route template를 우선 검토한다
- observability에서 로그와 메트릭의 카디널리티 전략을 혼동하지 않는다

## 10. 권장 기본 필드 세트

### 10.1 대표 요청 완료 로그

권장 필드:

- requestPath
- method
- status
- durationMs
- actorId(가능할 때)
- trace/correlation ID(패턴/MDC)

예:

```text
Completed request. requestPath=/api/v1/users/123 method=GET status=200 durationMs=34 actorId=u_001
```

### 10.2 대표 요청 실패 로그

권장 필드:

- requestPath
- method
- status
- errorCode
- durationMs
- actorId(가능할 때)
- trace/correlation ID

예:

```text
Failed request. requestPath=/api/v1/users method=POST status=500 errorCode=INTERNAL_SERVER_ERROR durationMs=88 actorId=u_001
```

### 10.3 외부 연동 실패 로그

권장 필드:

- requestPath
- actorId
- externalSystem
- status
- errorCode
- durationMs
- trace/correlation ID

## 11. 금지 규칙

다음은 기본 금지다.

- controller/service/util에서 SecurityContextHolder 직접 조회 후 제각각 principal 로깅
- requestPath, path, uri, url 등 키 이름 혼용
- query string 전체를 기본 로그에 포함
- principal 전체 객체 또는 JWT/raw token 덤프
- 요청 대표 로그를 여러 레이어에서 중복 출력
- MDC 값 정리 없이 다음 요청으로 누수
- traceId 없이 대표 오류 로그를 남겨 상관관계가 끊기는 것

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- trace/correlation 식별자가 대표 로그에 연결되는가?
- principal은 최소 식별자만 기록되는가?
- requestPath 필드명이 일관적인가?
- query string/민감정보를 기본으로 남기지 않는가?
- principal 기록 시점이 인증 완료 여부와 맞는가?
- 공통 요청 로그가 한 곳에서 일관되게 생성되는가?
- MDC/structured logging 전략이 누수 없이 운영 가능한가?
