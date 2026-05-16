# ADR-005: Keycloak 인증 사용자 내부 자동 등록

## Status

Accepted

## Date

2026-05-12

## Context

Keycloak 이 로그인과 계정 lifecycle 을 소유하면 auth-server 는 검증된 access token 으로 내부 사용자 레코드를 식별해야 합니다. 기존 결정은 내부 사용자가 없으면 `AUTH-004` 404 를 반환하는 방식이었지만, 이 방식은 Keycloak self-service registration 과 auth-server 의 내부 사용자 테이블을 별도 운영 절차로 동기화해야 했습니다.

자동 등록을 도입하더라도 email 을 계정 연결 키로 쓰면 안 됩니다. email 은 변경되거나 재사용될 수 있으므로 내부 권한 식별자는 계속 Keycloak `sub` 와 `(provider=KEYCLOAK, provider_subject=sub)` 조합이어야 합니다.

## Decision Drivers

- Keycloak self-service registration 이후 첫 API 호출에서 내부 사용자 레코드를 준비한다.
- 인증 주체와 token 발급 책임은 계속 Keycloak 에 둔다.
- email 은 충돌 검사에만 사용하고 내부 계정 연결 키로 사용하지 않는다.
- 내부 사용자 생성은 application use case 의 트랜잭션 경계 안에서 수행한다.
- Resource Server 부팅은 Keycloak discovery endpoint 가 떠 있어야만 성공하는 구조를 피한다.

## Considered Options

### Option 1: 내부 사용자가 없으면 계속 404 반환

- 장점: `GET /api/v1/auth/me` 가 순수 조회로 남고 쓰기 부작용이 없다.
- 단점: Keycloak 사용자와 내부 사용자 테이블을 별도 배치나 운영 절차로 맞춰야 한다.
- 트레이드오프: HTTP 의미는 단순하지만 운영 동기화 비용이 생긴다.

### Option 2: 첫 인증 요청에서 내부 사용자 자동 등록

- 장점: Keycloak 에서 계정을 만든 사용자가 첫 API 호출 시 바로 내부 사용자 레코드를 얻는다.
- 단점: `GET /api/v1/auth/me` 가 missing user 경로에서 쓰기를 수행한다.
- 트레이드오프: 사용자 온보딩은 단순해지지만 transaction, 충돌 처리, 감사 이벤트가 필요하다.

### Option 3: Keycloak Admin/Event API 로 사전 동기화

- 장점: API 요청 경로의 쓰기 부작용을 줄일 수 있다.
- 단점: 외부 API 호출, retry, idempotency, 실패 보상 흐름이 필요하다.
- 트레이드오프: 운영 복잡도가 커지고 Keycloak 이벤트 전달 신뢰성에 의존한다.

## Decision

Option 2 를 채택합니다. `GET /api/v1/auth/me` 는 검증된 Keycloak claim 의 `sub` 로 내부 사용자를 조회하고, 없으면 `email` 충돌을 먼저 검사한 뒤 내부 사용자를 자동 등록합니다. 동일 email 이 이미 다른 subject 에 연결되어 있으면 자동 연결하지 않고 `AUTH-005` 409 를 반환합니다.

## Consequences

### 긍정적 결과

- Keycloak self-service registration 과 내부 사용자 생성이 첫 API 호출에서 자연스럽게 이어진다.
- 내부 사용자 식별 기준은 여전히 immutable `sub` 이며, email 기반 자동 연결은 금지된다.
- 자동 등록 성공은 `KEYCLOAK_USER_AUTO_REGISTERED` 감사 이벤트로 남는다.

### 부정적 결과

- `GET /api/v1/auth/me` 는 missing user 경로에서 DB write 를 수행한다.
- 동시 첫 요청에서는 DB unique 제약과 충돌 처리 정책을 함께 고려해야 한다.
- 운영 환경은 `APP_SECURITY_KEYCLOAK_ISSUER_URI` 를 제공해야 한다.

### 위험 완화

- application use case 에 transaction boundary 를 둔다.
- 같은 email 이 이미 존재하면 `AUTH-005` 409 로 중단하고 새 subject 에 자동 연결하지 않는다.
- `(provider, provider_subject)` unique 제약으로 subject 중복 생성을 방지한다.
