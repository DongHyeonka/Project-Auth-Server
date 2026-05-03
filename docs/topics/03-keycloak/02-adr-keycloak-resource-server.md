# ADR-002: Keycloak을 인증 주체로 두고 auth-server를 Resource Server로 전환

## Status

Accepted

## Date

2026-04-17

## Context

기존 auth-server는 로컬 로그인, OAuth2 login callback, JWT 발급, issuer/JWKS 공개, Vault Transit 서명 위임까지 맡았습니다.
Keycloak이 이미 OIDC Provider와 broker 역할을 수행할 수 있으므로, 인증 책임을 애플리케이션에 계속 남기면 보안 경계와 운영 책임이 중복됩니다.

## Decision Drivers

- 로그인·회원가입·token 발급 책임을 전문 IdP로 집중한다.
- auth-server는 비즈니스 API와 내부 사용자 식별에 집중한다.
- DB 트랜잭션과 외부 인증 호출을 섞지 않는다.
- 자체 JWT signer와 Vault Transit dependency를 제거한다.
- Keycloak claim 변경의 영향 범위를 security adapter와 application command에 제한한다.

## Considered Options

### Option 1: auth-server가 계속 JWT를 발급

- 장점: 기존 `/auth/login`과 OAuth2 callback 흐름을 유지할 수 있습니다.
- 단점: issuer, signing key, JWKS, Vault Transit 운영 책임이 계속 남습니다.
- 트레이드오프: 구현 변경은 적지만 Keycloak 도입 효과가 약합니다.

### Option 2: Keycloak이 인증/인가 token을 발급하고 auth-server는 검증만 수행 (Keycloak-first)

- 장점: 로그인·회원가입·token 발급·issuer·JWKS·broker 책임이 Keycloak으로 모입니다. 내부 DB는 Keycloak token이 들어올 때 lazy upsert.
- 단점: 클라이언트 로그인 흐름과 테스트 데이터 준비 방식이 바뀝니다. 회원가입 UX는 Keycloak 테마로 흡수해야 합니다.
- 트레이드오프: 초기 전환 비용은 있지만 장기 운영 경계가 단순해집니다.

### Option 3: auth-server가 회원가입을 받고 Keycloak Admin API로 미러링

- 장점: 기존 signup UX를 유지할 수 있습니다.
- 단점: 이중 쓰기 실패 시 일관성 확보용 보상 로직(outbox/Saga)이 필요하고, password 원문이 auth-server를 통과해 보안 경계가 다시 넓어집니다. `no DB transaction held across remote call` 규칙 준수 비용도 큽니다.
- 트레이드오프: UX 유지 대가가 구조적 복잡도 증가로 직결됩니다.

## Decision

Option 2를 채택합니다. Keycloak을 인증 주체로 두고 auth-server는 Spring Security Resource Server로 token을 검증하며, 회원가입 UX와 계정 lifecycle은 Keycloak realm에 위임합니다. auth-server는 `(provider=KEYCLOAK, provider_subject=sub)` 기준으로 이미 연결된 내부 사용자만 조회합니다.

## Consequences

### 긍정적 결과

- 자체 JWT 발급 코드, 로컬 signup 컨트롤러, Vault Transit signer, password hasher가 모두 제거됩니다.
- issuer와 JWKS source of truth가 Keycloak 하나로 고정됩니다.
- Spring Security 타입은 `bootstrap`/`presentation` 경계에서 끝나고 application은 claim command만 받습니다.
- 내부 DB 스키마에서 `encoded_password`, LOCAL provider 분기, social subject 관련 체크 제약이 모두 제거됩니다(`V5__keycloak_only_provider.sql`).

### 부정적 결과

- 클라이언트는 더 이상 auth-server 로그인/회원가입 endpoint를 사용할 수 없습니다.
- 회원가입 UX가 Keycloak realm 설정과 테마에 묶입니다.
- Keycloak realm 설정은 API 인증의 필수 운영 의존성이며, 운영자는 JWKS 회전과 issuer URI 고정을 책임져야 합니다.

### 위험 완화

- `GET /api/v1/auth/me`는 내부 사용자 조회만 수행하고 token 검증은 Resource Server에 맡깁니다.
- 연결된 내부 사용자가 없으면 자동 생성이나 자동 연결 없이 `AUTH-004` 404를 반환합니다.
- role/claim mapping은 `KeycloakJwtAuthenticationConverter` 한 곳에 둡니다.
- 조회 흐름은 내부 DB lookup만 수행하므로 `GET`에 숨은 쓰기 부작용을 두지 않습니다.
