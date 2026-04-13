# Spring Security 트러블슈팅

## 한 줄 요약

현재 프로젝트의 보안 문제는 대부분 "Spring Security route / session", "Keycloak broker alias", "provider 매핑 규칙", "JWT 메타데이터", "Vault 설정" 다섯 축에서 발생합니다.  
아래 카탈로그는 증상 -> 원인 -> 해결 -> 검증 -> 교훈 순서로 바로 확인할 수 있게 정리한 것입니다.

## 배경

- 이 프로젝트는 `/login` 커스텀 로그인 페이지, OAuth2 redirect/callback, `/api/v1/auth/oauth2/complete`, 자체 JWT 발급, OIDC discovery를 함께 다룹니다.
- 그래서 문제를 볼 때 "어느 층에서 실패했는가"를 먼저 분리하지 않으면 원인 파악이 느려집니다.
- 특히 브라우저 로그인 흐름은 Keycloak, 세션, registration id, provider alias, JWT 설정이 연쇄적으로 연결돼 있습니다.

> **이슈 유형 라벨**: 아래 카탈로그의 각 이슈는 **[실제]** 또는 **[예상]** 라벨이 붙어 있습니다. [실제]는 개발 중 실제로 겪은 문제, [예상]은 현재 코드 구조상 발생 가능성이 높은 항목입니다.

## 증상

### 1. Google 버튼을 눌렀는데 Keycloak 일반 로그인 화면으로만 간다 [실제]

- `/api/v1/auth/oauth2/keycloak/google`로 들어가도 Google provider로 바로 분기되지 않습니다.

### 2. OAuth2 callback은 끝났는데 `/api/v1/auth/oauth2/complete`에서 실패한다 [실제]

- callback 이후 `/api/v1/auth/oauth2/complete`가 401이거나 기대한 JWT 응답을 주지 않습니다.

### 3. OAuth2 로그인에서 `AUTH-005 UNSUPPORTED_OAUTH_PROVIDER`가 난다 [예상]

- callback 이후 애플리케이션이 provider를 알 수 없다고 실패합니다.

### 4. 소셜 로그인은 성공했는데 `AUTH-003 OAUTH_ACCOUNT_CONFLICT`가 난다 [실제]

- Google/GitHub 로그인 직후 동일 이메일 기존 계정이 있어 가입이 안 됩니다.

### 5. 로컬 로그인에서 비밀번호가 맞는 것 같은데 계속 `AUTH-001`이다 [실제]

- 이메일과 비밀번호를 입력했는데 계속 invalid credentials가 반환됩니다.

### 6. JWT 검증자가 issuer 또는 `kid` mismatch를 보고한다 [예상]

- 발급된 토큰은 있어 보이는데 외부 서비스가 검증에 실패합니다.

### 7. Vault를 켰더니 서명이나 기동이 실패한다 [예상]

- `APP_SECURITY_JWT_VAULT_ENABLED=true` 이후 token 발급 또는 시작 과정에서 예외가 발생합니다.

## 원인 분석

### 1. Google 버튼을 눌렀는데 Keycloak 일반 로그인 화면으로만 간다

#### Root Cause

- `KeycloakIdpHintAuthorizationRequestResolver`가 넣는 `kc_idp_hint`와 Keycloak identity provider alias가 맞지 않습니다.
- 또는 `application.yml`의 `app.security.oauth2.*` 값과 실제 registration id가 어긋나 resolver가 hint를 추가하지 못합니다.

### 2. OAuth2 callback은 끝났는데 `/api/v1/auth/oauth2/complete`에서 실패한다

#### Root Cause

- `/api/v1/auth/oauth2/complete`는 공개 경로가 아니므로 callback 이후 인증 상태가 유지돼야 합니다.
- 브라우저 세션이 끊겼거나 redirect 흐름 중 쿠키/세션이 사라지면 controller가 `Authentication`을 주입받지 못합니다.

예상 에러 응답:

```
HTTP/1.1 401 Unauthorized

# 또는 Spring Security가 /login으로 redirect
302 Found → Location: /login
```

이때 application 로그에는 별다른 에러가 나타나지 않을 수 있습니다. 세션 문제는 framework 레벨에서 조용히 처리되기 때문입니다.

### 3. OAuth2 로그인에서 `AUTH-005 UNSUPPORTED_OAUTH_PROVIDER`가 난다

#### Root Cause

- 현재 mapper는 `authorizedClientRegistrationId` 문자열에 `google` 또는 `github`가 포함되는지 보고 provider를 추론합니다.
- registration id naming을 다른 형태로 바꾸면 provider 추론이 실패합니다.

예상 에러 응답:

```json
{
  "success": false,
  "code": "AUTH-005",
  "message": "지원하지 않는 OAuth2 공급자입니다.",
  "data": null
}
```

### 4. 소셜 로그인은 성공했는데 `AUTH-003 OAUTH_ACCOUNT_CONFLICT`가 난다

#### Root Cause

- `OAuthLoginService`는 provider+subject로 기존 소셜 계정을 찾고, 없으면 email 중복을 검사합니다.
- 이미 `LOCAL` 계정이나 다른 소셜 계정이 같은 이메일을 쓰고 있으면 충돌로 처리합니다.
- 이메일 unique constraint가 DB에 있기 때문에 최종 저장 단계에서도 충돌이 막힙니다.

### 5. 로컬 로그인에서 비밀번호가 맞는 것 같은데 계속 `AUTH-001`이다

#### Root Cause

- 사용자가 실제로는 `LOCAL` 계정이 아니라 `GOOGLE` 또는 `GITHUB` provider 계정일 수 있습니다.
- `LoginService`는 provider가 `LOCAL`이 아니면 비밀번호 비교 전에 바로 실패시킵니다.

### 6. JWT 검증자가 issuer 또는 `kid` mismatch를 보고한다

#### Root Cause

- `APP_SECURITY_JWT_ISSUER`가 실제 외부 접근 URL과 다를 수 있습니다.
- `active-key-id`와 JWK Set의 `kid`가 기대한 값과 다를 수 있습니다.
- 로컬에서 임시 키를 생성하면 재시작 후 키가 바뀔 수 있습니다.

### 7. Vault를 켰더니 서명이나 기동이 실패한다

#### Root Cause

- Vault 주소, token, mount path, transit key name 중 하나가 틀렸을 수 있습니다.
- transit key가 signing capability를 갖지 않거나 public key metadata 조회에 실패할 수 있습니다.

## 해결

### 1. Google 버튼을 눌렀는데 Keycloak 일반 로그인 화면으로만 간다

- Keycloak alias가 실제로 `google`, `github`인지 확인합니다.
- `application.yml`의 `app.security.oauth2.google-idp-hint`, `github-idp-hint` 값을 Keycloak alias와 맞춥니다.
- registration id가 바뀌었다면 `OAuth2LoginProperties`와 `KeycloakIdpHintAuthorizationRequestResolver` 조건을 함께 수정합니다.

### 2. OAuth2 callback은 끝났는데 `/api/v1/auth/oauth2/complete`에서 실패한다

- 브라우저가 세션 쿠키를 유지하는지 확인합니다.
- `SessionCreationPolicy.STATELESS` 같은 설정이 추가되지 않았는지 확인합니다.
- `OAuth2LoginSuccessHandler`가 실제로 `/api/v1/auth/oauth2/complete`로 redirect하는지 확인합니다.

### 3. OAuth2 로그인에서 `AUTH-005 UNSUPPORTED_OAUTH_PROVIDER`가 난다

- registration id에 `google` 또는 `github`가 포함되도록 유지합니다.
- naming 규칙을 바꾸고 싶다면 `OAuth2AuthenticationCommandMapper.resolveProvider()`를 함께 수정합니다.

### 4. 소셜 로그인은 성공했는데 `AUTH-003 OAUTH_ACCOUNT_CONFLICT`가 난다

- 현재 정책상 자동 계정 병합은 하지 않으므로 충돌 계정을 정리하거나 별도 linking 정책을 도입해야 합니다.
- 운영 중이라면 애플리케이션 로그뿐 아니라 DB의 기존 이메일 소유자와 provider 상태도 함께 확인합니다.

### 5. 로컬 로그인에서 비밀번호가 맞는 것 같은데 계속 `AUTH-001`이다

- 해당 사용자의 `provider` 값을 확인합니다.
- 소셜 계정이면 로컬 로그인 대신 OAuth2 로그인으로 들어가야 합니다.

### 6. JWT 검증자가 issuer 또는 `kid` mismatch를 보고한다

- `/.well-known/openid-configuration`의 `issuer`와 토큰 `iss`를 비교합니다.
- `/.well-known/jwks.json`의 `kid`와 JWT 헤더 `kid`를 비교합니다.
- 안정적인 환경에서는 `generate-key-pair-on-startup=false`와 고정 키 또는 Vault를 사용합니다.

### 7. Vault를 켰더니 서명이나 기동이 실패한다

- Vault에서 `transit/keys/<name>` 메타데이터를 직접 조회합니다.
- `supports_signing`, public key, mount path, transit key name을 확인합니다.
- 로컬 런북은 [../../development/vault-local-setup.md](../../development/vault-local-setup.md)를 참고합니다.

## 검증

### 1. Google 버튼을 눌렀는데 Keycloak 일반 로그인 화면으로만 간다

- `/oauth2/authorization/keycloak-google` 요청에 `kc_idp_hint=google`이 실제로 들어가는지 확인합니다.
- Keycloak이 Google provider 로그인 화면으로 바로 넘기는지 확인합니다.

### 2. OAuth2 callback은 끝났는데 `/api/v1/auth/oauth2/complete`에서 실패한다

- callback 직후 브라우저 요청이 `/api/v1/auth/oauth2/complete`로 이동하는지 확인합니다.
- 해당 요청에서 200 응답과 JWT payload가 내려오는지 확인합니다.

### 3. OAuth2 로그인에서 `AUTH-005 UNSUPPORTED_OAUTH_PROVIDER`가 난다

- `OAuth2AuthenticationToken.getAuthorizedClientRegistrationId()` 값을 로그 또는 디버거로 확인합니다.
- mapper가 기대하는 naming 규칙과 실제 값이 일치하는지 확인합니다.

### 4. 소셜 로그인은 성공했는데 `AUTH-003 OAUTH_ACCOUNT_CONFLICT`가 난다

- DB에서 같은 이메일을 가진 기존 계정과 provider 상태를 확인합니다.
- 신규 계정 저장 직전과 저장 실패 시점이 일치하는지 확인합니다.

### 5. 로컬 로그인에서 비밀번호가 맞는 것 같은데 계속 `AUTH-001`이다

- 대상 사용자의 `provider`가 `LOCAL`인지 먼저 확인합니다.
- `LOCAL` 계정이라면 저장된 `encoded_password`와 BCrypt 비교가 통과하는지 확인합니다.

### 6. JWT 검증자가 issuer 또는 `kid` mismatch를 보고한다

- 토큰 `iss`, 헤더 `kid`, discovery 문서 `issuer`, JWK `kid` 네 값을 한 번에 비교합니다.
- 재시작 이후 `kid` 또는 public key가 바뀌었는지도 확인합니다.

### 7. Vault를 켰더니 서명이나 기동이 실패한다

- Vault key metadata 조회가 성공하는지 확인합니다.
- 애플리케이션이 같은 mount path와 transit key name으로 sign 요청을 보내는지 확인합니다.

## 교훈

### 1. Google 버튼을 눌렀는데 Keycloak 일반 로그인 화면으로만 간다

- 브로커 구조에서는 registration id, Keycloak alias, 버튼 이름이 서로 다른 개념입니다.

### 2. OAuth2 callback은 끝났는데 `/api/v1/auth/oauth2/complete`에서 실패한다

- 최종 결과가 JWT라고 해서 OAuth2 handshake 전체가 stateless한 것은 아닙니다.

### 3. OAuth2 로그인에서 `AUTH-005 UNSUPPORTED_OAUTH_PROVIDER`가 난다

- 현재 구조는 provider 추론이 설정값 완전 자유도보다 naming convention에 더 의존합니다.

### 4. 소셜 로그인은 성공했는데 `AUTH-003 OAUTH_ACCOUNT_CONFLICT`가 난다

- 동일 이메일이라고 해서 서로 다른 인증 수단의 계정을 자동 병합하는 것은 보안상 위험할 수 있습니다.

### 5. 로컬 로그인에서 비밀번호가 맞는 것 같은데 계속 `AUTH-001`이다

- "같은 이메일"과 "같은 인증 방식"은 다릅니다.

### 6. JWT 검증자가 issuer 또는 `kid` mismatch를 보고한다

- 토큰 검증 이슈는 "서명 실패"보다 "메타데이터 불일치"인 경우가 더 많습니다.

### 7. Vault를 켰더니 서명이나 기동이 실패한다

- Vault 연동 문제는 애플리케이션 코드보다 운영 설정 검증이 먼저입니다.

## 외부 발행 메모

- 공개용 제목 후보:
  - "OAuth2 callback은 성공했는데 왜 `/complete`에서 401이 날까"
  - "`kc_idp_hint`를 넣었는데도 Keycloak 일반 로그인 화면으로 가는 이유"
- 공개 시 제거할 정보:
  - 내부 환경 변수 이름과 조직 특화 Vault 경로
- 독자에게 가장 전달하고 싶은 교훈:
  - OAuth2 문제는 한 지점이 아니라 브라우저, 세션, provider alias, JWT 메타데이터를 함께 봐야 빨리 풀립니다.
- 공개용 초안 파일:
  - `docs/publish/spring-security-why-oauth2-login-still-needs-session.md`
  - `docs/publish/spring-security-keycloak-kc-idp-hint.md`
