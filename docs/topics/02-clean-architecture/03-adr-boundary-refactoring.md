# ADR-003: AGENTS 기준 레이어 경계 리팩토링

## Status

Accepted

## Date

2026-04-14

## Context

`AGENTS.md`와 standards/examples는 레이어별 소유 책임을 명확히 나누지만, 일부 구현은 편의상 경계 관심사를 직접 들고 있었다.

- `presentation` 응답 모델이 MDC와 현재 시각을 직접 읽었다.
- OAuth2 callback controller와 mapper가 Spring Security 타입과 provider registration 해석을 직접 다뤘다.
- repository adapter가 transaction boundary를 소유했다.
- Vault Transit client가 ad-hoc JSON/URL/header 조립을 핵심 로직 안에 섞고 있었다.

이 결정은 기능을 바꾸기보다, 같은 인증 플로우를 유지하면서 경계 위반 가능성을 줄이기 위한 것이다.

## Decision Drivers

- 응답 DTO/model은 순수 transport 계약이어야 한다.
- 현재 사용자와 provider registration 해석은 web/security boundary에서 끝나야 한다.
- transaction은 application use case 작업 단위에서 소유해야 한다.
- infrastructure adapter는 기술 번역을 담당하되 business workflow를 소유하지 않아야 한다.
- 같은 위반이 재발하지 않도록 ArchUnit 테스트로 구조를 검증해야 한다.

## Considered Options

### Option 1: 기존 구조 유지 후 국소 수정

- 장점: 수정 파일 수가 적다.
- 단점: `ApiResult`, controller, mapper, repository adapter에 섞인 책임이 남아 같은 문제가 반복된다.
- 트레이드오프: 단기 변경은 작지만 standards/examples와 계속 어긋난다.

### Option 2: 경계별 소유자를 다시 지정

- 장점: `presentation`은 HTTP 계약, `bootstrap`은 request/security context, `application`은 transaction, `infrastructure`는 기술 번역에 집중한다.
- 단점: 조립 코드와 테스트 fixture 수정이 필요하다.
- 트레이드오프: 초기 변경량은 늘지만 이후 기능 추가 시 경계 판단 비용이 줄어든다.

## Decision

Option 2를 선택해 request metadata, OAuth2 provider mapping, transaction boundary, external integration DTO/translation의 소유 위치를 각 레이어 기준에 맞게 재배치한다.

## Consequences

### 긍정적 결과

- `ApiResult`는 MDC나 clock을 모르는 순수 envelope가 되었다.
- OAuth2 controller는 Spring Security `Authentication` 대신 presentation 전용 principal을 받는다.
- provider registration ID와 redirect path는 bootstrap security configuration에서 조립한다.
- repository adapter의 transaction annotation을 제거하고 application collaborator가 transaction boundary를 가진다.
- Vault Transit client는 typed request/response와 명시적 infrastructure exception translation을 사용한다.
- ArchUnit이 presentation의 SecurityContext 직접 접근, ApiResult의 시간/MDC 의존, infrastructure transaction 소유를 막는다.

### 부정적 결과

- controller/advice 생성자에 `ApiResultFactory` 의존성이 추가된다.
- use case 내부 collaborator가 늘어나면서 bootstrap bean wiring이 길어졌다.
- OAuth2 callback 테스트는 Spring Security token 대신 전용 principal fixture를 사용한다.

### 위험 완화

- 외부 API 응답 shape와 endpoint path는 유지한다.
- `./gradlew test`로 application, presentation, infrastructure, bootstrap 테스트를 모두 통과시킨다.
- DB provider별 필수 필드는 Flyway constraint로 한 번 더 보호한다.

## 후속 정리

본 ADR 시점에 정리된 Vault Transit client 는 이후 [ADR-002 Keycloak 전환](../03-keycloak/02-adr-keycloak-resource-server.md) 에서 자체 JWT 발급 책임이 사라지면서 통째로 제거됨. 자세한 제거 범위는 [04-adr-token-ownership-cleanup.md](../03-keycloak/04-adr-token-ownership-cleanup.md).
