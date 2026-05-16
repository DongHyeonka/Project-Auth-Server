# Keycloak 로컬 설정

이 문서는 로컬 개발 환경에서 Keycloak을 띄우고 연결하는 **runbook**입니다.
Keycloak의 아키텍처, Identity Broker 패턴, 프로젝트 적용 배경은 [docs/topics/03-keycloak/README.md](../../topics/03-keycloak/README.md)를 참고합니다.

## 기본 전제

- `docker compose`는 `deploy/docker/docker-compose.yml`을 사용합니다.
- 실제 소셜 로그인용 `Google`/`GitHub` Client ID와 Secret은 auth-server가 아니라 Keycloak에 등록합니다.
- **회원가입과 로그인 UX는 Keycloak realm이 담당합니다.** auth-server는 Keycloak이 발급한 token을 검증만 하며, signup/login endpoint나 password 저장을 가지지 않습니다.
- auth-server는 Keycloak issuer를 Resource Server 설정으로 사용하고, token 발급 client secret을 보관하지 않습니다.

## 1. 로컬 인프라 실행

`.env.local` 값을 현재 환경에 맞게 확인한 뒤 아래 명령으로 실행합니다.

```bash
docker compose --env-file .env.local up -d
```

- PostgreSQL: `localhost:5432`
- Keycloak: `http://localhost:8081`

## 2. Keycloak 초기 상태

처음 기동하면 아래 내용이 자동으로 import 됩니다.

- realm: `project-auth`
- client: `project-auth-server`

위 값은 로컬 개발 기준 고정 예시입니다. 운영이나 공유 환경에서는 환경값에 맞춰 다시 설정해야 합니다.

## 3. Realm 회원가입(Self-service registration) 활성화

auth-server는 회원가입을 받지 않으므로 Keycloak realm의 self-service registration을 켭니다.

1. `project-auth` realm 선택
2. `Realm Settings → Login` 탭
3. `User registration`: **ON**
4. 필요 시 `Email as username`, `Verify email`, `Forgot password` 활성화
5. `Authentication → Password policy`에서 최소 길이/복잡도 정책 설정

결과: Keycloak의 로그인 화면에 "Register" 링크가 노출되고, 사용자는 Keycloak에서 직접 계정을 만듭니다.

## 4. Google Identity Provider 등록

Keycloak Admin Console에서 아래 순서로 진행합니다.

1. `project-auth` realm 선택
2. `Identity providers`
3. `Google` 추가
4. alias를 `google`로 설정
5. Google Cloud Console에서 발급한 Client ID / Client Secret 입력
6. Keycloak이 보여주는 Redirect URI를 Google OAuth 설정에 등록

## 5. GitHub Identity Provider 등록

1. `project-auth` realm 선택
2. `Identity providers`
3. `GitHub` 추가
4. alias를 `github`로 설정
5. GitHub OAuth App에서 발급한 Client ID / Client Secret 입력
6. Keycloak이 보여주는 Redirect URI를 GitHub OAuth 설정에 등록

## 6. Realm role / token claim 점검

auth-server는 다음 claim을 신뢰합니다(상세: [03-claim-role-design.md](../../topics/03-keycloak/03-claim-role-design.md)).

- `sub`, `email`, `name`, `preferred_username`
- `realm_access.roles` (Spring authority `ROLE_*`)
- `scope` (Spring authority `SCOPE_*`)

점검 항목:

1. `Realm roles`에서 `user`, `admin` 생성
2. `Realm Settings → User registration`의 기본 역할에 `user` 포함
3. `Client scopes → roles → Mappers`에 `realm roles` mapper가 활성화되어 있는지 확인 (기본 제공)
4. `Client scopes → email` / `profile`이 project-auth-server client의 default client scope에 들어 있는지 확인

## 7. auth-server와 맞춰야 하는 값

auth-server의 로컬 설정은 아래 값을 기준으로 Keycloak과 연결됩니다.

- issuer: `http://localhost:8081/realms/project-auth`
- Spring 설정: `spring.security.oauth2.resourceserver.jwt.issuer-uri`

즉 auth-server는 Keycloak token의 `iss`와 realm JWKS를 검증합니다. 소셜 로그인 분기는 Keycloak 또는 프론트엔드 로그인 흐름에서 처리합니다.

## 8. auth-server 실행

```bash
set -a
source .env.local
set +a

./gradlew :bootstrap:bootRun
```

## 9. 인증된 호출 예시

Keycloak에서 access token을 받은 뒤 auth-server에는 Bearer token으로 요청합니다.

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8081/realms/project-auth/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=project-auth-server" \
  -d "username=<kc-user>" \
  -d "password=<kc-password>" \
  -d "scope=openid profile email" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/auth/me
```

첫 호출 시 auth-server가 token의 `sub`로 내부 DB에 사용자 행을 lazy-create하고, 두 번째 호출부터는 기존 내부 식별자를 재사용합니다.
같은 email 이 이미 다른 Keycloak subject 에 연결되어 있으면 auth-server는 자동 연결하지 않고 `AUTH-005` / `409 Conflict`를 반환합니다.
