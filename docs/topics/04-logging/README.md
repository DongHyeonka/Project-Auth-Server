# Logging

요청 상관관계 / 예외 처리 / 감사 로그 / 운영 수집 계약 4 축의 설계 결정과 운영 런북.

## 핵심 질문

- 왜 MDC 를 로그 메시지 안에서 직접 꺼내 쓰면 안 되는가?
- 왜 audit / access 의 의미 필드는 message 문자열이 아니라 structured field 여야 하는가?
- 왜 Kubernetes 운영에서는 audit file 을 기본값이 아니라 opt-in 계약으로 둬야 하는가?

## 현재 프로젝트 맥락

현재 로깅은 다섯 축으로 정리된다.

1. `TraceIdFilter` 가 32 자리 hex `traceId`, 축약된 `clientIp`, 정규화된 `userAgent` 를 MDC 에 넣고 `X-Trace-Id` 를 응답 헤더에 기록.
2. prod profile 은 Spring Boot structured console appender 를 사용하고, MDC 와 SLF4J key-value pair 를 JSON top-level field 로 출력.
3. `RequestAccessLogFilter` 는 noisy 경로를 제외한 요청을 `http.access` 로 요약하고, `status`, `durationMs`, `actorId`, `remoteIp` 등을 message 가 아니라 event field 로 기록.
4. 서비스 레이어는 logger 대신 `AuthAuditEventPublisher` 로 audit event 를 발행하고, bootstrap listener 가 `eventType`, `emailMasked`, `userIdHash`, `provider`, `reason` 등을 structured field 로 기록.
5. `audit.auth` 는 기본적으로 콘솔을 source of truth 로 사용. 롤링 파일 appender 는 `audit-file` profile 을 켰을 때만 추가.

## 문서

| 문서 | 내용 |
|------|------|
| [01-architecture.md](./01-architecture.md) | MDC, structured event fields, access log, exception log, optional audit file 의 역할 분리와 설계 이유 |
| [02-runbook-log-correlation-and-dev-actuator.md](./02-runbook-log-correlation-and-dev-actuator.md) | traceId 상관관계, structured JSON 확인, trusted proxy, audit file opt-in, dev actuator 검증 절차 |

## 핵심 키워드

`MDC` · `traceId` · `structured logging` · `SLF4J key-value pairs` · `http.access` · `audit.auth` · `audit-file profile` · `trusted proxy` · `clientIp` · `userAgent` · `actorId` · `emailMasked`

## 관련 문서

- 에러 처리와 traceId 배경: [../02-clean-architecture/02-error-handling.md](../02-clean-architecture/02-error-handling.md)
