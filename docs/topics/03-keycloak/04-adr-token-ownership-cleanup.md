# ADR-004: Keycloak 전환 후 자체 인증 자산 일괄 제거

## Status

Accepted

## Date

2026-04-17 (ADR-002 와 동일 PR 묶음)

## Context

[ADR-002](./02-adr-keycloak-resource-server.md) 가 인증 주체를 Keycloak 으로 옮긴 직후, auth-server 내부에는 다음이 *기능적으로 무용해진* 상태로 남아 있었다.

- 자체 JWT 발급기 (`NimbusJwtTokenIssuerAdapter`, RSA key source, Vault Transit signer)
- 로컬 회원가입 / 로그인 endpoint, BCrypt password hasher, password 도메인 규칙
- 자체 OIDC discovery / JWKS endpoint
- Multi-provider (LOCAL / GOOGLE / GITHUB) 분기

이걸 그대로 두면 *"어느 issuer 의 token 을 신뢰하는가"*, *"회원 식별자의 source of truth 는 어디인가"* 가 흐려지고, 운영 장애 가능 지점도 늘어난다 (예: 로컬 RSA key 파일 누락 시 부팅 실패, Vault Transit endpoint 변경 시 영향 등).

## Decision Drivers

- Keycloak 으로 인증 주체가 옮겨졌으므로, auth-server 의 *모든* 인증 발급 / 키 관리 책임은 중복.
- `provider` 컬럼이 `LOCAL/GOOGLE/GITHUB/KEYCLOAK` 4 종으로 분기되어 있으면 auth lookup 쿼리, audit event, error code 모두 분기 비용이 남는다.
- password 가 디스크에 저장되는 한 *비밀번호 정책 / 해시 회전 / 누설 시 회전* 책임이 따라온다 — IdP 가 처리하는 게 정석.
- 부분 제거 (deprecation 표기 후 점진 제거) 는 6~12 개월 dead code 가 portfolio 에 남는 비용이 큼.

## Considered Options

### Option 1: 점진적 deprecation (각 클래스에 `@Deprecated` + 주석)

- 장점: 기존 클라이언트가 일시적으로 호환됨.
- 단점: dead code 가 PR diff 마다 노이즈가 됨. 보안 책임 (password 저장, 자체 키 보관) 이 *제거 전까지* 계속 살아 있음.
- 트레이드오프: portfolio 관점에서 *"제거를 못 끝내는 사람"* 시그널.

### Option 2: 일괄 제거 + DB 스키마 정리 (V4 → V5 마이그레이션 2 단)

- 장점: 책임 경계가 한 PR 로 깨끗하게 정리됨. password / Vault Transit 운영 위험이 즉시 사라짐.
- 단점: 기존 `/auth/login` / `/auth/oauth2/*` 클라이언트가 곧장 깨짐 (다만 Keycloak 으로 이미 옮겼으니 이 시점에 클라이언트는 없음).
- 트레이드오프: 초기 변경량이 크지만, 이후 운영 표면이 작아짐.

## Decision

**Option 2 채택.** 자체 인증 발급 / 로컬 회원가입 / Vault Transit signing 자산을 일괄 제거하고, DB 스키마는 `V4__add_keycloak_provider.sql` (KEYCLOAK 값 허용) → `V5__keycloak_only_provider.sql` (encoded_password 컬럼 제거 + LOCAL/social 체크 제약 제거 + `provider` 체크를 `KEYCLOAK` 단일값으로 고정) 두 단계로 정리.

## 제거된 책임

| 이전 사용처 | 역할 | 처리 |
|------------|------|------|
| `AuthLoginController` | `/api/v1/auth/login` 로컬 로그인 endpoint | 제거 |
| `AuthOAuth2Controller` | Keycloak broker 시작 / 완료 endpoint | 제거 |
| `OAuth2SecurityConfiguration` | OAuth2 login filter chain | `ResourceServerSecurityConfiguration` 으로 대체 |
| `UserSignUpController` / `SignUp*` | 로컬 회원가입 use case 와 DTO | 제거 (회원가입은 Keycloak 담당) |
| `BcryptPasswordEncoderAdapter` / `PasswordHasherPort` | bcrypt 비밀번호 해싱 | 제거 (auth-server 는 password 를 다루지 않음) |
| `EncodedPassword` / `UserPasswordPolicy` / `InvalidUserPasswordException` | 비밀번호 도메인 규칙 | 제거 |
| `User.registerLocal` | LOCAL provider 등록 경로 | 제거 (`registerKeycloak` 만 남음) |
| `AuthProvider.LOCAL/GOOGLE/GITHUB` | social/local provider 분기 값 | 제거 (`KEYCLOAK` 만 남음) |
| `NimbusJwtTokenIssuerAdapter` | 자체 RS256 JWT 발급 | 제거 |
| `JwtKeyConfiguration` / `ConfiguredJwtSigningKeySource` | 로컬 RSA signer 구성 | 제거 |
| `VaultTransitClient` / `VaultTransitJwtSigner` | Vault Transit 서명 API 호출 | 제거 |
| `ConfiguredOpenIdDiscoveryDocumentProvider` / `OpenIdDiscoveryController` | 자체 issuer / JWKS 공개 | 제거 |
| `auth-login.html` | auth-server 로그인 페이지 | 제거 |
| `AuthAuditEventType.LOGIN_*` / `OAUTH_LOGIN_*` / `TOKEN_ISSUED` / `SIGNUP_*` | 로컬 인증 / 발급 감사 이벤트 | `KEYCLOAK_USER_NOT_FOUND` 로 축소 |
| `AuthErrorCode.INVALID_CREDENTIALS` / `OAUTH_*` | 로컬 로그인 에러 코드 | `KEYCLOAK_CLAIMS_INVALID` / `KEYCLOAK_ACCOUNT_CONFLICT` 로 교체 |
| `UserErrorCode.*` / `ApiSuccessCode.USER_SIGNED_UP` | 로컬 회원가입 에러 / 성공 코드 | 제거 |

## 남은 책임

| 현재 사용처 | 역할 |
|------------|------|
| `ResourceServerSecurityConfiguration` | Keycloak issuer 기반 Bearer token 검증 |
| `KeycloakJwtAuthenticationConverter` | claim / role → project principal 매핑 |
| `AuthenticatedUserController` | 현재 사용자 조회 진입점 |
| `KeycloakUserLoader` | `(KEYCLOAK, sub)` 기준 내부 사용자 식별 |
| `KeycloakUserClaimsValidator` | 검증된 JWT 에서 올라온 claim 의 내부 도메인 적합성 확인 |

## DB 스키마 변화

- `V4__add_keycloak_provider.sql`: `KEYCLOAK` 값을 허용하는 중간 단계 migration
- `V5__keycloak_only_provider.sql`: `encoded_password` 컬럼과 LOCAL / social 체크 제약을 제거하고, `provider_subject` 를 NOT NULL 로, `provider` 체크를 `KEYCLOAK` 단일값으로 고정

## Consequences

### 긍정적 결과

- auth-server 는 더 이상 token 을 만들거나 공개키를 배포하거나 password 를 저장하지 않습니다.
- issuer / JWKS source of truth 가 Keycloak 한 곳으로 고정.
- DB 스키마에서 `encoded_password`, LOCAL provider 분기, social subject 체크 제약이 모두 제거 — 코드 분기뿐 아니라 *데이터 모델* 도 단순해짐.
- Vault Transit endpoint 의존성이 사라져 dev 환경에서 Vault 가 secret store 역할만 하면 됨.

### 부정적 결과

- 클라이언트는 더 이상 auth-server 로그인 / 회원가입 endpoint 를 사용할 수 없음 (이 시점에 그런 클라이언트는 없었음 — pre-emptive 제거).
- 회원가입 UX 가 Keycloak realm 설정 / 테마에 묶임.
- Keycloak realm 설정은 API 인증의 필수 운영 의존성 — 운영자는 JWKS 회전과 issuer URI 고정을 책임.

### 위험 완화

- `GET /api/v1/auth/me` 의 token 검증은 ResourceServer 가 담당.
- 연결된 내부 사용자가 없으면 [ADR-005](./05-adr-keycloak-user-auto-registration.md)에 따라 email 충돌 검사 후 내부 사용자 자동 등록.
- role / claim mapping 은 `KeycloakJwtAuthenticationConverter` 한 곳에 모임.
- 조회 흐름은 내부 DB lookup 만 수행하므로 `GET` 에 숨은 쓰기 부작용 없음.

## 관련 문서

- [02-adr-keycloak-resource-server.md](./02-adr-keycloak-resource-server.md) — 인증 주체 이전 결정 (ADR-002)
- [05-adr-keycloak-user-auto-registration.md](./05-adr-keycloak-user-auto-registration.md) — Keycloak 인증 사용자 내부 자동 등록 결정 (ADR-005)
- [01-architecture.md](./01-architecture.md) — 현재 ResourceServer 아키텍처
- [03-claim-role-design.md](./03-claim-role-design.md) — claim / role 매핑 정책
