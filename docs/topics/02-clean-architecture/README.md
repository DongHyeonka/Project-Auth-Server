# Clean Architecture

5 모듈 (`bootstrap` / `domain` / `application` / `presentation` / `infrastructure`) 의 경계 결정과 계층별 책임을 정리한다.

## 문서

| 문서 | 내용 |
|------|------|
| [02-error-handling.md](./02-error-handling.md) | 예외 처리 핵심 아키텍처 — `ErrorCode` 가 HTTP status 를 모름. 계층별 책임 분리 + Security 필터 / Infrastructure 번역 |
| [02a-validation-deep-dive.md](./02a-validation-deep-dive.md) | Validation 응답 정규화 (`Map<String, List<String>>`) + `ConstraintViolation` 의미 + `@ConfigurationProperties` 검증과의 차이 |
| [03-adr-boundary-refactoring.md](./03-adr-boundary-refactoring.md) | AGENTS 기준으로 응답 / 인증 / 트랜잭션 / 인프라 경계를 재정렬한 ADR-003 |

## 검증

이 폴더가 약속하는 모든 경계 규칙은 [`LayerDependencyArchitectureTest`](../../../bootstrap/src/test/java/com/project/auth/architecture/LayerDependencyArchitectureTest.java) 에서 ArchUnit 으로 PR 마다 자동 검증.

## 관련 문서

- [Architecture Overview](../../architecture/README.md)
- [README · Highlighted Engineering Decisions](../../../README.md#highlighted-engineering-decisions)
