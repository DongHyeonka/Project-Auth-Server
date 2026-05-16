# Topic Docs

토픽별 아키텍처 결정 기록 (ADR) 과 운영 런북 / 트러블슈팅을 모은 폴더.

## 토픽 인덱스

| 주제 | 핵심 내용 |
|------|------|
| [02-clean-architecture](./02-clean-architecture/README.md) | 5 모듈 레이어 경계 ADR + 예외 처리 5+1 계층 + sealed `ClientFacingErrorCode` 정책 + Validation deep-dive |
| [03-keycloak](./03-keycloak/README.md) | Keycloak ResourceServer 전환 ADR + claim / role 설계 + 자체 인증 자산 제거 ADR |
| [04-logging](./04-logging/README.md) | MDC traceId (+ sentinel `-` 폴백), structured logging, audit 단일 채널, LogSanitizer 회귀 게이트 |

## 정책/테스트 단일 출처

상위 hub 에 모인 *정책 단일 출처* 문서는 토픽 문서가 참조합니다 (어긋날 경우 정책 문서가 우선).

- [`exception-handling-policy.md`](../exception-handling-policy.md) — 13개 예외 처리 정책
- [`testing-coverage-policy.md`](../testing-coverage-policy.md) — JaCoCo / PIT / jqwik Tier + 임계치
- [`testing-history/`](../testing-history/README.md) — 사건 단위 before/after 비교 기록
