# Keycloak 로컬 설정

## 기본 전제

- `docker compose`는 `deploy/docker/docker-compose.yml`을 사용합니다.
- 실제 소셜 로그인용 `Google`/`GitHub` Client ID와 Secret은 auth-server가 아니라 Keycloak에 등록합니다.
- auth-server는 Keycloak realm의 OIDC client 정보만 사용합니다.

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
- client secret: `project-auth-server-secret`

위 값은 로컬 개발 기준 고정 예시입니다. 운영이나 공유 환경에서는 환경값에 맞춰 다시 설정해야 합니다.

## 3. Google Identity Provider 등록

Keycloak Admin Console에서 아래 순서로 진행합니다.

1. `project-auth` realm 선택
2. `Identity providers`
3. `Google` 추가
4. alias를 `google`로 설정
5. Google Cloud Console에서 발급한 Client ID / Client Secret 입력
6. Keycloak이 보여주는 Redirect URI를 Google OAuth 설정에 등록

## 4. GitHub Identity Provider 등록

1. `project-auth` realm 선택
2. `Identity providers`
3. `GitHub` 추가
4. alias를 `github`로 설정
5. GitHub OAuth App에서 발급한 Client ID / Client Secret 입력
6. Keycloak이 보여주는 Redirect URI를 GitHub OAuth 설정에 등록

## 5. auth-server와 맞춰야 하는 값

auth-server의 로컬 설정은 아래 값을 기준으로 Keycloak과 연결됩니다.

- issuer: `http://localhost:8081/realms/project-auth`
- client id: `project-auth-server`
- client secret: `project-auth-server-secret`
- Google provider alias: `google`
- GitHub provider alias: `github`

즉 Keycloak 쪽 alias와 auth-server의 `kc_idp_hint` 값이 맞아야 소셜 로그인 분기가 정상 동작합니다.

## 6. auth-server 실행

```bash
set -a
source .env.local
set +a

./gradlew :bootstrap:bootRun
```

브라우저에서 아래 URL로 진입하면 Keycloak 로그인 흐름을 확인할 수 있습니다.

- `http://localhost:8080/api/v1/auth/oauth2/keycloak/google`
- `http://localhost:8080/api/v1/auth/oauth2/keycloak/github`
