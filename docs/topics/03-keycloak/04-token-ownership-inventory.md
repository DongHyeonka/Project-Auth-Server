# 자체 JWT / Vault Transit / 로컬 회원가입 사용처 정리

## Why

Keycloak으로 인증 주체를 이전하면 auth-server 내부의 token 발급, signing key, Vault Transit 호출, 로컬 회원가입 UX는 책임이 중복됩니다.
남겨두면 “어느 issuer의 token을 신뢰하는가”, “회원 식별자의 source of truth는 어디인가”가 흐려지고 운영 장애 지점도 늘어납니다.

## What

제거한 책임:

| 이전 사용처 | 역할 | 처리 |
|------------|------|------|
| `AuthLoginController` | `/api/v1/auth/login` 로컬 로그인 endpoint | 제거 |
| `AuthOAuth2Controller` | Keycloak broker 시작/완료 endpoint | 제거 |
| `OAuth2SecurityConfiguration` | OAuth2 login filter chain | Resource Server 설정으로 대체 |
| `UserSignUpController` / `SignUp*` | 로컬 회원가입 use case와 DTO | 제거 (회원가입은 Keycloak 담당) |
| `BcryptPasswordEncoderAdapter` / `PasswordHasherPort` | bcrypt 비밀번호 해싱 | 제거 (auth-server는 password를 다루지 않음) |
| `EncodedPassword` / `UserPasswordPolicy` / `InvalidUserPasswordException` | 비밀번호 도메인 규칙 | 제거 |
| `User.registerLocal` | LOCAL provider 등록 경로 | 제거 (`registerKeycloak`만 남음) |
| `AuthProvider.LOCAL/GOOGLE/GITHUB` | social/local provider 분기 값 | 제거 (`KEYCLOAK`만 남음) |
| `NimbusJwtTokenIssuerAdapter` | 자체 RS256 JWT 발급 | 제거 |
| `JwtKeyConfiguration` / `ConfiguredJwtSigningKeySource` | 로컬 RSA signer 구성 | 제거 |
| `VaultTransitClient` / `VaultTransitJwtSigner` | Vault Transit 서명 API 호출 | 제거 |
| `ConfiguredOpenIdDiscoveryDocumentProvider` / `OpenIdDiscoveryController` | 자체 issuer/JWKS 공개 | 제거 |
| `auth-login.html` | auth-server 로그인 페이지 | 제거 |
| `AuthAuditEventType.LOGIN_*`/`OAUTH_LOGIN_*`/`TOKEN_ISSUED`/`SIGNUP_*` | 로컬 인증·발급 감사 이벤트 | `KEYCLOAK_USER_SYNC_SUCCESS`/`KEYCLOAK_USER_SYNC_CONFLICT`로 교체 |
| `AuthErrorCode.INVALID_CREDENTIALS`/`OAUTH_*` | 로컬 로그인 에러 코드 | `KEYCLOAK_CLAIMS_INVALID`/`KEYCLOAK_ACCOUNT_CONFLICT`로 교체 |
| `UserErrorCode.*` / `ApiSuccessCode.USER_SIGNED_UP` | 로컬 회원가입 에러/성공 코드 | 제거 |

남긴 책임:

| 현재 사용처 | 역할 |
|------------|------|
| `ResourceServerSecurityConfiguration` | Keycloak issuer 기반 Bearer token 검증 |
| `KeycloakJwtAuthenticationConverter` | claim/role -> project principal 매핑 |
| `AuthenticatedUserController` | 현재 사용자 조회와 내부 사용자 동기화 진입점 |
| `KeycloakUserSynchronizer` | `(KEYCLOAK, sub)` 기준 내부 사용자 식별/생성 |
| `KeycloakUserClaimsValidator` | 검증된 JWT에서 올라온 claim의 내부 도메인 적합성 확인 |

스키마 변화:

- `V4__add_keycloak_provider.sql`: `KEYCLOAK` 값을 허용하는 중간 단계 migration
- `V5__keycloak_only_provider.sql`: `encoded_password` 컬럼과 LOCAL/social 체크 제약을 제거하고, `provider_subject`를 NOT NULL로, `provider` 체크를 `KEYCLOAK` 단일값으로 고정

## Result

auth-server는 더 이상 token을 만들거나 공개키를 배포하거나 password를 저장하지 않습니다.
회원가입/로그인/소셜 연동은 Keycloak realm에서 모두 처리되며, auth-server는 Keycloak이 발급한 token이 처음 들어올 때 내부 DB에 사용자 행을 lazy하게 만드는 역할만 남깁니다.
운영자는 Keycloak realm의 issuer URI와 JWKS를 source of truth로 봐야 하며, auth-server는 DB 사용자 행과 비즈니스 권한 정책만 다룹니다.
