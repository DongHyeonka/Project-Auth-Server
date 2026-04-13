# Logging 문서

## 개요

이 폴더는 인증 서버의 로깅 구조를 "요청 상관관계", "예외 처리", "감사 로그", "운영 수집 계약" 관점에서 다시 정리한 문서 모음입니다.  
핵심 질문은 세 가지입니다.

- 왜 MDC를 로그 메시지 안에서 직접 꺼내 쓰면 안 되는가?
- 왜 audit/access의 의미 필드는 message 문자열이 아니라 structured field여야 하는가?
- 왜 Kubernetes 운영에서는 audit file을 기본값이 아니라 opt-in 계약으로 둬야 하는가?

## 학습 배경

- 이전 구조는 `traceId`를 MDC에 넣고도 일부 로그 메시지에서 다시 직접 문자열로 붙이고 있었습니다.
- 서비스 레이어의 실패 로그와 글로벌 예외 핸들러 로그가 겹치면서 같은 실패가 두 번 기록될 수 있었습니다.
- `audit.auth`는 보안 이벤트를 담고 있었지만, 의미 필드가 message 문자열에 묻히면 검색과 집계가 regex에 의존하게 됩니다.
- 인증 성공/실패 로그에는 요청자 IP, User-Agent 같은 핵심 식별 정보가 자동으로 남지 않았습니다.
- proxy 뒤에서 얻은 client IP가 진짜 사용자 IP인지 판단하려면 trusted proxy 범위가 운영 설정으로 고정되어야 합니다.

## 현재 프로젝트 맥락

현재 로깅은 다섯 축으로 정리됩니다.

1. `TraceIdFilter`가 32자리 hex `traceId`, 축약된 `clientIp`, 정규화된 `userAgent`를 MDC에 넣고 `X-Trace-Id`를 응답 헤더에 기록합니다.
2. prod profile은 Spring Boot structured console appender를 사용하고, MDC와 SLF4J key-value pair를 JSON top-level field로 출력합니다.
3. `RequestAccessLogFilter`는 noisy 경로를 제외한 요청을 `http.access`로 요약하고, `status`, `durationMs`, `actorId`, `remoteIp` 등을 message가 아니라 event field로 남깁니다.
4. `LoginService`, `SignUpService`, `OAuthLoginService`는 logger 대신 `AuthAuditEventPublisher`로 audit event를 발행하고, bootstrap listener가 `eventType`, `emailMasked`, `userIdHash`, `provider`, `reason` 등을 structured field로 기록합니다.
5. `audit.auth`는 기본적으로 콘솔을 source of truth로 사용합니다. 롤링 파일 appender는 `audit-file` profile을 켰을 때만 추가됩니다.

## 문서 구조

| 문서 | 내용 |
|------|------|
| [01-architecture.md](./01-architecture.md) | MDC, structured event fields, access log, exception log, optional audit file의 역할 분리와 설계 이유 |
| [02-runbook-log-correlation-and-dev-actuator.md](./02-runbook-log-correlation-and-dev-actuator.md) | traceId 상관관계, structured JSON 확인, trusted proxy, audit file opt-in, dev actuator 검증 절차 |

## 핵심 키워드

`MDC` · `traceId` · `structured logging` · `SLF4J key-value pairs` · `http.access` · `audit.auth` · `audit-file profile` · `trusted proxy` · `clientIp` · `userAgent` · `actorId` · `emailMasked`

## 읽는 순서

1. [01-architecture.md](./01-architecture.md)
2. [02-runbook-log-correlation-and-dev-actuator.md](./02-runbook-log-correlation-and-dev-actuator.md)

## 관련 문서

- 에러 처리와 traceId 배경: [../02-clean-architecture/02-error-handling.md](../02-clean-architecture/02-error-handling.md)
- 인증 흐름 배경: [../01-spring-security/01-architecture.md](../01-spring-security/01-architecture.md)
