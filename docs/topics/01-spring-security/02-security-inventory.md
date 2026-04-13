# 적용된 Security Inventory

## Why

Spring Security를 사용한다고 해서 일반적으로 떠올리는 보안 기능이 전부 자동으로 들어오지는 않습니다.  
현재 프로젝트에 실제로 적용된 보안 기능과 의도적으로 비워 둔 기능을 분리해 적어 두어야 아래 질문에 정확히 답할 수 있습니다.

- 이 서버의 실제 인증 진입점은 어디인가?
- OAuth2 provider 분기는 누가 하는가?
- JWT 검증을 위한 메타데이터는 어디서 공개하는가?
- 어떤 보안 제약은 코드가 아니라 DB 스키마가 보장하는가?

## What

### 1. 적용된 기능 전체 목록

| 번호 | 기능 | 핵심 코드 | 현재 의미 |
|------|------|-----------|-----------|
| 1 | HTTP 보안 진입점 | `OAuth2SecurityConfiguration` | 공개/보호 경로 분리와 OAuth2 로그인 진입점 구성 |
| 2 | 커스텀 로그인 페이지 | `AuthLoginPageController`, `auth-login.html` | 브라우저 사용자가 `/login`에서 로컬 로그인과 소셜 로그인 시작점을 함께 본다 |
| 3 | OAuth2 Client + OIDC | `spring-boot-starter-oauth2-client`, `application-*.yml` | Keycloak realm을 OIDC provider로 사용한다 |
| 4 | Keycloak provider 분기 | `KeycloakIdpHintAuthorizationRequestResolver` | `kc_idp_hint`로 Google/GitHub broker alias를 지정한다 |
| 5 | OAuth2 성공 후 이동 | `OAuth2LoginSuccessHandler` | callback 성공 후 `/api/v1/auth/oauth2/complete`로 redirect한다 |
| 6 | OAuth2 실패 응답 | `OAuth2LoginFailureHandler` | 실패 시 JSON 에러 코드와 HTTP status를 반환한다 |
| 7 | Principal 해석 | `OAuth2AuthenticationCommandMapper` | `Authentication`에서 provider, subject, email, name을 추출한다 |
| 8 | 로컬 로그인 검증 | `LoginService` | 이메일 조회, `LOCAL` provider 확인, BCrypt 비교 후 JWT를 발급한다 |
| 9 | 소셜 로그인 등록/재로그인 | `OAuthLoginService` | provider+subject로 기존 사용자를 찾고, 없으면 신규 등록 후 JWT를 발급한다 |
| 10 | 회원가입 비밀번호 보호 | `SignUpRequest`, `SignUpCommandValidator`, `UserPasswordPolicy`, `BcryptPasswordEncoderAdapter` | 입력 검증, 도메인 정책, 해시 생성이 단계별로 분리돼 있다 |
| 11 | JWT access token 발급 | `NimbusJwtTokenIssuerAdapter` | RS256 access token을 발급한다 |
| 12 | 키 소스 전환 | `ConfiguredJwtSigningKeySource`, `VaultTransitJwtSigningKeySource`, `JwtKeyConfiguration` | 로컬 RSA 또는 Vault Transit signer로 전환 가능하다 |
| 13 | OIDC discovery / JWK 공개 | `ConfiguredOpenIdDiscoveryDocumentProvider`, `OpenIdDiscoveryController` | 외부 검증자를 위해 issuer와 공개키 메타데이터를 노출한다 |
| 14 | 영속 무결성 제약 | `UserJpaEntity`, `JpaUserRepositoryAdapter`, `V1__create_users_table.sql`, `V2__add_oauth_login_columns.sql` | 이메일과 `(provider, provider_subject)` 유일성을 DB 레벨에서 보장한다 |
| 15 | 입력/에러 표준화 | DTO validation, `ValidationExceptionHandler`, `RequestExceptionHandler`, `ApplicationExceptionHandler`, `InfrastructureExceptionHandler`, `ApiErrorHttpStatusMapper` | 잘못된 로그인/가입/OAuth2 정보와 내부 장애에 일관된 코드와 상태값을 부여한다 |

### 2. 적용되지 않은 기능 목록

| 기능 | 현재 상태 | 근거 |
|------|-----------|------|
| Method Security | 미적용 | `@EnableMethodSecurity`, `@PreAuthorize`, `@Secured` 검색 결과 없음 |
| Role 기반 인가 | 미적용 | `hasRole`, `hasAuthority` 사용 없음 |
| OAuth2 Resource Server | 미적용 | `oauth2ResourceServer`, `JwtDecoder` 설정 없음 |
| Spring Authorization Server | 미적용 | 관련 dependency와 config 없음 |
| Remember-me | 미적용 | 설정 없음 |
| Form Login / HTTP Basic | 미적용 | `formLogin()`, `httpBasic()` 설정 없음 |
| Logout 커스터마이징 | 미적용 | `logout()` 설정 없음 |
| Refresh Token 발급 | 미적용 | 토큰 모델과 응답 DTO가 access token만 다룸 |

## How

### 1. 브라우저 진입점과 HTTP 경계 보호

가장 바깥 보안 경계는 `SecurityFilterChain`입니다.  
현재 프로젝트는 "공개 경로는 명시적으로 열고, 나머지는 기본적으로 닫는다" 전략을 취합니다.

- 브라우저 로그인 진입점: `/login`
- 공개 경로: health, Swagger, 회원가입, 로컬 로그인, OAuth2 시작/callback, OIDC discovery
- 보호 경로: 그 외 전체

특히 `/login`이 중요한 이유는 이 프로젝트가 브라우저 사용자를 위해 정적 로그인 허브 페이지를 제공하기 때문입니다.  
이 화면에서 사용자는 로컬 로그인과 Google/GitHub broker 시작 링크를 모두 확인할 수 있습니다.

### 2. OAuth2 / OIDC 계층

이 프로젝트의 OAuth2 login은 Google/GitHub와 직접 붙지 않습니다.

- auth-server는 Keycloak realm을 OIDC provider로 봅니다.
- Google/GitHub는 Keycloak broker가 대신 연동합니다.
- auth-server는 `kc_idp_hint`만 넣어 "어느 upstream provider로 보낼지"를 Keycloak에 알려 줍니다.

즉 외부 provider별 프로토콜 차이는 Keycloak로 밀어내고, 애플리케이션은 OIDC client 역할만 수행합니다.

### 3. Spring Security 객체와 애플리케이션 객체의 경계

Spring Security 안에서 인증이 완료되면 presentation 계층은 `Authentication`을 받습니다.  
하지만 application service는 Spring Security 타입을 직접 알지 않도록 `OAuthLoginCommand`만 받습니다.

이 변환 경계가 `OAuth2AuthenticationCommandMapper`입니다.

- 입력: `Authentication`, `OAuth2AuthenticationToken`, `OidcUser`
- 출력: provider, providerSubject, email, name

이 구조 덕분에 `OAuthLoginService`는 framework 타입 없이도 테스트 가능합니다.

### 4. 로컬 로그인과 회원가입

현재 로컬 로그인은 Spring Security `AuthenticationProvider` 체인을 사용하지 않습니다.

- 로그인:
  `LoginService`가 이메일 조회, `LOCAL` provider 확인, BCrypt 비교, JWT 발급을 순서대로 처리합니다.
- 회원가입:
  `SignUpRequest` -> `SignUpCommandValidator` -> `UserPasswordPolicy` -> `BcryptPasswordEncoderAdapter` 순으로 raw password를 검증하고 해시합니다.

즉 로컬 로그인과 회원가입은 "명시적인 유스케이스"로 설계돼 있습니다.

### 5. 토큰, 키, discovery

JWT 발급 자체는 `NimbusJwtTokenIssuerAdapter`가 맡고, 실제 키 material과 signer 구성은 별도 레이어가 공급합니다.

- 로컬/일반 환경:
  `ConfiguredJwtSigningKeySource` + `RSASSASigner`
- Vault 환경:
  `VaultTransitJwtSigningKeySource` + `VaultTransitJwtSigner`

토큰 발급과 key source를 분리해 두었기 때문에, 애플리케이션은 같은 발급기 로직을 유지한 채 운영 환경에 따라 signer 전략만 바꿀 수 있습니다.

### 6. 영속 무결성과 충돌 방지

문서화에서 자주 빠지는 지점이지만, 현재 사용자 식별 제약은 애플리케이션 코드만이 아니라 DB 스키마도 함께 보장합니다.

- `V1__create_users_table.sql`:
  `email` unique constraint
- `V2__add_oauth_login_columns.sql`:
  `(provider, provider_subject)` unique constraint
- `JpaUserRepositoryAdapter`:
  저장 시 `DataIntegrityViolationException`을 `DuplicateUserEmailException`으로 변환

즉 "동일 이메일 충돌"과 "같은 provider subject 중복"은 단지 서비스 로직의 관례가 아니라, persistence 계층과 migration이 함께 지키는 계약입니다.

## Result

현재 inventory를 기준으로 security 구조를 한 줄로 요약하면 아래와 같습니다.

> Spring Security는 브라우저 로그인 진입과 OAuth2/OIDC 기반 HTTP 인증 경계를 맡고, application service는 사용자 검증과 JWT 발급을 맡으며, persistence와 migration은 사용자 식별 무결성을 보장한다.

동시에 아래도 분명히 말할 수 있어야 합니다.

> 이 프로젝트는 아직 role 기반 인가, resource server 검증, refresh token, method security를 구현하지 않았다.

## References

- 관련 코드:
  - `bootstrap/src/main/java/com/project/auth/config/auth/OAuth2SecurityConfiguration.java`
  - `bootstrap/src/main/java/com/project/auth/config/auth/security/KeycloakIdpHintAuthorizationRequestResolver.java`
  - `bootstrap/src/main/java/com/project/auth/config/auth/security/OAuth2LoginSuccessHandler.java`
  - `bootstrap/src/main/java/com/project/auth/config/auth/security/OAuth2LoginFailureHandler.java`
  - `presentation/src/main/java/com/project/auth/presentation/auth/mapper/OAuth2AuthenticationCommandMapper.java`
  - `application/src/main/java/com/project/auth/application/auth/login/LoginService.java`
  - `application/src/main/java/com/project/auth/application/auth/oauth/login/OAuthLoginService.java`
  - `infrastructure/src/main/java/com/project/auth/infrastructure/security/token/NimbusJwtTokenIssuerAdapter.java`
  - `bootstrap/src/main/java/com/project/auth/openid/ConfiguredOpenIdDiscoveryDocumentProvider.java`
  - `infrastructure/src/main/java/com/project/auth/infrastructure/persistence/user/JpaUserRepositoryAdapter.java`
  - `infrastructure/src/main/resources/db/migration/V1__create_users_table.sql`
  - `infrastructure/src/main/resources/db/migration/V2__add_oauth_login_columns.sql`
- 관련 문서:
  - [01-architecture.md](./01-architecture.md)
  - [03-authentication-flow.md](./03-authentication-flow.md)
  - [04-token-password-and-discovery.md](./04-token-password-and-discovery.md)
