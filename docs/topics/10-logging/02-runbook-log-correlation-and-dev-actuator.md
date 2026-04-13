# Runbook: 로그 상관관계, structured field, dev actuator 검증

## 1. 목적

이 절차는 logger 리뷰 반영 후 아래 항목이 실제로 동작하는지 확인하기 위한 운영 점검 절차입니다.

- 응답 헤더, 응답 body, 일반 로그, access log, audit log가 같은 `traceId`로 연결되는가?
- `traceId`가 32자리 lowercase hex 형식인가?
- prod structured console에서 `eventType`, `reason`, `status`, `durationMs`, `actorId` 같은 값이 message가 아니라 JSON field로 보이는가?
- audit/access log에 축약된 `clientIp`, 정규화된 `userAgent`, 축약된 `remoteIp`, 축약된 `actorId`가 운영 가정에 맞게 남는가?
- access log가 noisy 경로를 제외하는가?
- audit file을 켜는 환경에는 persistent volume 또는 file shipper 계약이 있는가?
- dev actuator가 `127.0.0.1:9090`에서만 열리고, 허용한 endpoint만 노출되는가?

## 2. 사용 시점

- `TraceIdFilter`, `RequestAccessLogFilter`, `AuthAuditLoggingConfiguration`, `logback-spring.xml`을 수정한 직후
- `logging.structured.*`, `server.forward-headers-strategy`, `server.tomcat.remoteip.internal-proxies`를 바꾼 직후
- audit file 경로, 롤링 정책, `audit-file` profile 사용 여부를 바꾼 직후
- "traceId는 있는데 요청 재구성이 끊긴다" 같은 피드백이 나왔을 때

## 3. Preconditions

- 필요한 권한:
  - 로컬에서 애플리케이션을 실행하고 콘솔 로그와 audit 파일을 볼 수 있어야 합니다.
- 필요한 환경 변수/도구:
  - `curl`
  - `rg`
  - 선택 사항: `jq`, `ss`
- 사전 확인 사항:
  - prod structured logging 점검은 `prod` profile 또는 운영 로그 backend에서 확인합니다.
  - local/dev plain console은 `%kvp`로 key-value field를 보여주지만, JSON field 검증의 기준은 prod structured console입니다.
  - Kubernetes에서는 console 수집 backend가 audit/access 로그의 source of truth입니다.
  - audit file은 기본으로 켜지지 않습니다. 파일 검증을 하려면 `audit-file` profile과 `APP_LOGGING_AUDIT_FILE` 값을 먼저 확인합니다.
  - proxy 뒤 운영 환경은 `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`에 ingress/load balancer/service mesh 대역을 설정해야 합니다.
  - `dev` profile 점검 시 management 포트 `9090`이 사용 가능해야 합니다.

## 4. Procedure

### Step 1

인증이 필요한 endpoint를 익명으로 호출해 traceId를 확보합니다.

```bash
curl -i http://localhost:8080/api/v1/auth/oauth2/complete
```

예상 결과:

- 응답은 `401 Unauthorized`
- `X-Trace-Id` 헤더가 존재
- 응답 body의 `traceId`가 헤더와 동일
- `traceId` 값은 32자리 lowercase hex
- 콘솔 로그에는 메시지 본문이 아니라 패턴 prefix에서 `traceId=...`가 보여야 함

### Step 2

prod structured console 또는 로그 backend에서 방금 받은 traceId를 조회합니다.

```text
traceId = <STEP1_TRACE_ID>
```

예상 결과:

- `AUTHENTICATION_REQUIRED` 또는 관련 audit event가 검색됨
- `traceId`, `clientIp`, `userAgent`, `eventType`, `actorId`, `method`, `requestPath`가 JSON field로 보임
- message는 `AUTHENTICATION_REQUIRED`처럼 event 이름만 담고, `actorId=... method=...` 문자열을 합친 형태가 아님

### Step 3

access log의 structured field를 확인합니다.

```bash
curl -i http://localhost:8080/api/v1/auth/me \
  -H 'User-Agent: runbook-access-check'
```

예상 결과:

- `http.access` 로그의 message는 `ACCESS`
- `eventType=HTTP_ACCESS`, `status`, `durationMs`, `remoteIp`, `actorId`, `result`가 event field로 존재
- `status`와 `durationMs`는 문자열 parsing 대상이 아니라 숫자 field로 집계 가능

### Step 4

가짜 `X-Forwarded-For`를 넣어도 앱 코드가 raw header를 직접 읽지 않는지 확인합니다.

```bash
curl -i http://localhost:8080/login \
  -H 'X-Forwarded-For: 203.0.113.10' \
  -H 'User-Agent: runbook-forwarded-check'
```

예상 결과:

- 응답 자체는 정상
- 로컬 직접 호출처럼 요청이 trusted proxy를 거치지 않으면 `remoteIp`와 `clientIp`는 raw `X-Forwarded-For` 값이 아니라 실제 접속 IP를 축약한 값
- 운영 proxy 뒤에서는 `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`가 실제 proxy 대역과 일치할 때만 forwarded client IP가 반영됨
- `userAgent`는 MDC field로 기록되며 CR/LF, 공백, 제어문자, `=`, `|`가 `_`로 치환됨

### Step 5

noisy 경로가 access log 제외 대상인지 확인합니다.

```bash
curl -i http://localhost:8080/livez
curl -i http://localhost:8080/swagger-ui.html
```

예상 결과:

- 응답 자체는 정상
- 콘솔에는 위 요청에 대한 `ACCESS ...` 로그가 기본적으로 남지 않음

### Step 6

audit file은 명시적으로 opt-in 했을 때만 확인합니다.

```bash
SPRING_PROFILES_ACTIVE=local,audit-file ./gradlew :bootstrap:bootRun
rg 'eventType=' logs/audit/auth.log
```

예상 결과:

- `audit-file` profile을 켠 경우에만 `logs/audit/auth.log`가 생성됨
- 파일 로그에는 `traceId`, `clientIp`, `userAgent`와 `%kvp` 기반 event fields가 함께 보임
- Kubernetes에서 이 profile을 켠다면 persistent volume 또는 file shipper가 있어야 함

### Step 7

dev actuator가 loopback 전용으로 열리고, 허용 대상 endpoint만 응답하는지 확인합니다.

```bash
ss -ltn | rg ':9090'
curl -s http://127.0.0.1:9090/actuator
curl -s http://127.0.0.1:9090/actuator/loggers
curl -i http://127.0.0.1:9090/actuator/env
```

예상 결과:

- `9090`은 `127.0.0.1`에만 바인딩
- `/actuator`, `/actuator/loggers`는 응답
- `/actuator/env`는 `403` 또는 `404`

## 5. Verification

- 같은 요청 하나를 기준으로 `X-Trace-Id`, body `traceId`, structured console 또는 로그 backend를 연결할 수 있어야 합니다.
- prod structured console에는 `traceId`, `clientIp`, `userAgent`, audit/access event fields가 JSON field로 보여야 합니다.
- audit file은 `audit-file` profile을 켠 환경에서만 생성되어야 합니다.
- access log 메시지 본문에는 `traceId=`가 직접 들어가지 않아야 합니다.
- audit/access 메시지 본문에는 `reason=`, `status=`, `actorId=` 같은 검색 필드가 직접 합쳐져 있지 않아야 합니다.
- 인증 audit event에는 이메일 원문이 아니라 `emailMasked`, 사용자 ID 원문이 아니라 `userIdHash`가 남아야 합니다.
- `/livez`, `/readyz`, `/swagger-ui`, `/actuator/**`는 기본 access log에서 빠져야 합니다.
- dev actuator는 로컬 loopback에서만 열려야 합니다.

## 6. Rollback / Recovery

- prod structured field가 사라지면 `logging.structured.format.console=logstash`, `structured-console-appender.xml`, SLF4J key-value logging 호출부를 함께 확인합니다.
- audit 파일 경로가 잘못되면 `APP_LOGGING_AUDIT_FILE` 또는 `app.logging.audit.file`을 되돌립니다.
- Kubernetes에서 file logging이 불필요하면 `audit-file` profile을 제거합니다.
- access log 제외가 과하면 `app.logging.access.excluded-path-prefixes`에서 경로를 제거합니다.
- 프록시 환경에서 remote IP가 비정상적이면 `server.forward-headers-strategy`, `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`, ingress forwarded header 설정을 함께 검토합니다.
- dev actuator 접근이 필요 이상으로 막히면 `DevActuatorConfiguration` 허용 경로와 `management.server.address`를 함께 확인합니다.

## 7. Failure Modes

- 자주 발생하는 실수:
  - audit/access 필드를 message 문자열에 `key=value`로 다시 합치는 방식으로 회귀함
  - prod structured console이 아니라 local plain console만 보고 JSON field 검증을 끝냄
  - `audit-file` profile만 켜고 persistent volume 또는 file shipper 계약을 빼먹음
  - `X-Forwarded-For` 헤더가 곧바로 `remoteIp`가 되는 것을 정상이라고 오해함
  - User-Agent, exception reason, path 같은 외부 입력을 정규화 없이 audit/access field로 넣음
- 위험한 포인트:
  - audit 파일이 컨테이너 내부 로컬 디스크에만 있으면 장기 보존은 여전히 약합니다.
  - trusted proxy 범위가 너무 넓으면 위조된 forwarded header를 믿을 수 있습니다.
  - excluded 경로를 너무 넓게 잡으면 실제 필요한 인증 흐름도 access log에서 사라질 수 있습니다.

## 8. References

- 관련 대시보드:
  - 현재 없음. 콘솔 로그와 audit file, 플랫폼 수집 로그를 기준으로 확인합니다.
- 관련 문서:
  - [01-architecture.md](./01-architecture.md)
  - [../02-clean-architecture/02-error-handling.md](../02-clean-architecture/02-error-handling.md)
