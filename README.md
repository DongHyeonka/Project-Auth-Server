# Project-Auth-Server

인증과 인가를 담당하는 서버입니다.

## 현재 브랜치 범위

- 회원 도메인 구성
- 회원가입 API 구현
- 로그인 API 및 JWT 발급 기반 구현
- JPA 기반 사용자 영속성 어댑터 구성
- Flyway 기반 스키마 마이그레이션 추가
- Keycloak 기반 OAuth2 소셜 로그인 시작점 구성
- Keycloak 로그인 성공 후 auth-server 내부 JWT 재발급 흐름 추가
- `local`, `dev`, `prod` 환경 설정 분리
- `.env` 실행 설정 파일과 로컬 `docker compose` 실행 기준 추가
- auth-server 정적 로그인 페이지 추가
- RS256 기반 JWT 발급과 JWK Set 공개
- `api-server`, `worker` 검증 준비용 well-known 메타데이터 추가

## 모듈 구성

- `bootstrap`
  - 애플리케이션 실행 모듈입니다.
  - Spring Boot 설정과 런타임 구성을 담당합니다.
- `presentation`
  - Controller, 요청/응답 DTO, Mapper를 담당합니다.
  - 외부 요청을 애플리케이션 유스케이스로 전달하는 진입점입니다.
- `application`
  - 유스케이스, 커맨드/결과 DTO, 포트를 담당합니다.
  - 도메인 규칙을 조합해 실제 요청 흐름을 처리합니다.
- `domain`
  - 엔티티, 값 객체, 도메인 서비스, 도메인 예외를 담당합니다.
  - 특정 프레임워크나 저장소 구현에 의존하지 않습니다.
- `infrastructure`
  - 영속성 어댑터, 외부 시스템 연동, 보안 연동 구현을 담당합니다.
  - Keycloak, DB, Kafka 같은 외부 자원과 맞닿는 계층입니다.
- `common`
  - 공통 응답 모델과 외부 계층에서 함께 사용하는 공통 요소를 담당합니다.

## 모듈 내부 패키지 규칙

- `bootstrap`
  - 애플리케이션 조립과 설정만 둡니다.
  - 예: `config/auth`, `config/openapi`
- `application`
  - 기능별 유스케이스를 기준으로 둡니다.
  - 유스케이스 안에서 `port/in`, `port/out`으로 구분합니다.
  - 예: `auth/login/port/in`, `auth/login/port/out`, `user/signup/port/in`, `user/signup/port/out`, `support/exception`
- `domain`
  - 도메인 개념별로 묶습니다.
  - 예: `user/model`, `user/exception`, `user/service`
- `presentation`
  - 외부 진입점도 기능별로 나눕니다.
  - 예: `auth/controller`, `auth/dto`, `user/controller`, `support/exception`
- `infrastructure`
  - 기술 구현과 대상 자원 기준으로 나눕니다.
  - 예: `persistence/user/entity`, `persistence/user/repository`, `persistence/user/mapper`, `security/password`, `security/token`

새 기능을 추가할 때는 먼저 `기능(auth, user 등)`을 고르고, 그 안에서 `controller`, `dto`, `service`, `exception`처럼 책임에 맞는 위치에 배치하는 것을 기본 규칙으로 삼습니다.

## 현재 구현 방향

- `bootstrap -> presentation, infrastructure`
- `presentation -> application, common`
- `infrastructure -> application, domain`
- `application -> domain`
- `domain -> none`
- `common -> none`

- 실행 가능한 Spring Boot 모듈은 `bootstrap` 하나만 둡니다.
- 현재 브랜치에서는 PostgreSQL + JPA + Flyway 기준으로 회원가입/로그인 흐름을 검증합니다.
- OAuth2는 Keycloak을 OIDC 공급자로 사용하고, Google/GitHub 브로커는 `kc_idp_hint`로 분기합니다.
- 응답은 공통 응답 형식으로 감싸서 반환합니다.
- JWT는 auth-server가 RS256으로 직접 발급하고 `issuer`, `key pair`, `expiration`은 설정값으로 관리합니다.
- JPA는 `ddl-auto=validate`로만 두고, 스키마 변경은 Flyway 스크립트로 관리합니다.
- 실행 설정 파일은 `bootstrap` 모듈에 두고, 실제 값은 프로파일별 yml과 환경 변수로 분리합니다.

## 현재 브랜치에서 확인할 수 있는 기능

- 회원 도메인, 값 객체, 회원가입/로그인 유스케이스
- 회원가입 API, 로그인 API, 공통 응답 구조
- Keycloak Google/GitHub 소셜 로그인 진입 API
- OAuth2 로그인 성공 후 auth-server 자체 JWT 재발급
- JPA 사용자 엔티티, Spring Data JPA 저장소, 영속성 매퍼
- Flyway 마이그레이션 스크립트
- Swagger 기반 API 문서
- Postman 컬렉션 기반 수동 검증
- PostgreSQL + Keycloak 로컬 인프라 구성
- auth-server 로그인 페이지(`/login`)
- JWT 공개키 노출 엔드포인트
  - `GET /.well-known/openid-configuration`
  - `GET /.well-known/jwks.json`

## 환경 설정 전략

프로파일은 `local`, `dev`, `prod`로 분리합니다.

- `application.yml`
  - 공통 설정만 둡니다.
  - 프로파일 공통 JPA, OpenAPI, OAuth2 registration id 같은 값을 관리합니다.
- `application-local.yml`
  - 로컬 개발용 기본값을 둡니다.
  - 기본 datasource는 H2를 사용합니다.
  - 필요하면 환경 변수로 PostgreSQL 연결값을 덮어쓸 수 있습니다.
- `application-dev.yml`
  - 개발 환경에서 필요한 값을 환경 변수로 주입받습니다.
- `application-prod.yml`
  - 운영 환경에서 필요한 값을 환경 변수로 주입받습니다.

`.env` 파일은 Spring Boot가 직접 읽는 파일이라기보다, 로컬 셸이나 `docker compose`, IDE 실행 설정이 환경 변수로 주입할 수 있도록 돕는 실행 설정 파일로 사용합니다.

- `.env.local`
- `.env.dev`
- `.env.prod`

## 로컬 인프라

로컬에서는 루트의 `docker-compose.yml`로 PostgreSQL, Keycloak, Vault를 함께 띄웁니다.

- PostgreSQL
  - auth-server용 DB: `project_auth`
  - Keycloak용 DB: `keycloak`
- Keycloak
  - PostgreSQL을 외부 DB로 사용합니다.
  - 로컬 realm import 파일은 `docs/keycloak/realm/project-auth-realm-local.json`에 둡니다.
- Vault
  - dev mode로 기동합니다.
  - `vault-init` 서비스가 transit engine과 로컬 signing key를 자동으로 준비합니다.

같은 PostgreSQL 인스턴스를 쓰더라도 auth-server와 Keycloak은 DB를 분리합니다. 애플리케이션 테이블과 Keycloak 관리 테이블을 한 DB에 섞지 않는 것을 기본 기준으로 잡습니다.

## 실행 방법

먼저 `.env.local` 값을 현재 로컬 환경에 맞게 확인한 뒤, 로컬 인프라를 실행합니다.

```bash
docker compose --env-file .env.local up -d
```

이후 현재 셸에 환경 변수를 올린 뒤 애플리케이션을 실행합니다.

```bash
set -a
source .env.local
set +a

./gradlew :bootstrap:bootRun
```

애플리케이션이 실행되면 기본 포트는 `8080`입니다.

애플리케이션 실행 전에 대상 DB와 Keycloak이 먼저 떠 있어야 합니다.
로컬 Keycloak 설정 절차는 `docs/keycloak/LOCAL_SETUP.md` 문서를 기준으로 맞춥니다.
Vault Transit 로컬 확인 절차는 `docs/vault/LOCAL_SETUP.md`를 참고합니다.

브라우저에서 `http://localhost:8080/login` 으로 들어가면 auth-server가 직접 제공하는 로그인 페이지를 확인할 수 있습니다.

로컬에서 환경 변수를 따로 주지 않으면 H2 메모리 DB 기준으로 실행됩니다. 기존 `.env.local`에 `APP_DATASOURCE_*` 값이 들어 있으면 그 값이 우선 적용되어 PostgreSQL로 연결됩니다.

필수 DB 설정은 아래 환경 변수로 제어합니다.

- `APP_DATASOURCE_URL`
- `APP_DATASOURCE_USERNAME`
- `APP_DATASOURCE_PASSWORD`

JWT 설정은 아래 환경 변수로 덮어쓸 수 있습니다.

- `APP_SECURITY_JWT_ISSUER`
- `APP_SECURITY_JWT_ACTIVE_KEY_ID`
- `APP_SECURITY_JWT_GENERATE_KEY_PAIR_ON_STARTUP`
- `APP_SECURITY_JWT_ACCESS_TOKEN_EXPIRATION`
- `APP_SECURITY_JWT_KEYS_0_KEY_ID`
- `APP_SECURITY_JWT_KEYS_0_PUBLIC_KEY`
- `APP_SECURITY_JWT_KEYS_0_PRIVATE_KEY`
- `APP_SECURITY_JWT_KEYS_1_KEY_ID`
- `APP_SECURITY_JWT_KEYS_1_PUBLIC_KEY`
- `APP_SECURITY_JWT_KEYS_1_PRIVATE_KEY`
- `APP_SECURITY_JWT_VAULT_ENABLED`
- `APP_SECURITY_JWT_VAULT_ADDRESS`
- `APP_SECURITY_JWT_VAULT_TOKEN`
- `APP_SECURITY_JWT_VAULT_MOUNT_PATH`
- `APP_SECURITY_JWT_VAULT_TRANSIT_KEY_NAME`

Keycloak OAuth2 설정은 아래 환경 변수로 제어합니다.

- `APP_SECURITY_OAUTH2_KEYCLOAK_ISSUER_URI`
- `APP_SECURITY_OAUTH2_KEYCLOAK_CLIENT_ID`
- `APP_SECURITY_OAUTH2_KEYCLOAK_CLIENT_SECRET`
- `APP_SECURITY_OAUTH2_GOOGLE_IDP_HINT`
- `APP_SECURITY_OAUTH2_GITHUB_IDP_HINT`

로컬 인프라용 환경 변수는 아래 파일에서 함께 관리합니다.

- `.env.local`
  - auth-server 로컬 실행
  - `docker compose` 로컬 인프라 실행
- `.env.dev`
  - 개발 환경 예시
- `.env.prod`
  - 운영 환경 예시

## 애플리케이션 이미지 빌드

루트의 [Dockerfile](/home/donghyeon/dev/Project-Auth-Server/Dockerfile)로 `auth-server` 이미지를 빌드할 수 있습니다.

현재 Dockerfile은 멀티스테이지 빌드로 동작합니다.

1. Gradle로 `:bootstrap:bootJar` 생성
2. BuildKit cache mount로 Gradle 캐시 재사용
3. Spring Boot `jarmode=tools`로 layered jar를 추출
4. dependency / boot loader / application 레이어를 분리 복사
5. 최종 JRE 이미지에 필요한 레이어만 복사
6. non-root 사용자로 애플리케이션 실행
7. `/actuator/health` 기반 Docker healthcheck 포함

로컬 빌드:

```bash
docker build -t project-auth-server:local .
```

로컬 실행 예시:

```bash
docker run --rm -p 8080:8080 \
  --env-file .env.local \
  -e SPRING_PROFILES_ACTIVE=local \
  project-auth-server:local
```

같은 이미지를 `dev`, `prod`에서 함께 사용하려면 런타임에 `SPRING_PROFILES_ACTIVE`만 다르게 주입하면 됩니다.

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e APP_DATASOURCE_URL=jdbc:postgresql://host:5432/project_auth \
  -e APP_DATASOURCE_USERNAME=project_auth \
  -e APP_DATASOURCE_PASSWORD=project_auth \
  project-auth-server:local
```

컨테이너에서 JVM 옵션이 필요하면 `JAVA_TOOL_OPTIONS`로 주입합니다. 현재 엔트리포인트는 `java -jar /app/application.jar` 형태라 JVM이 `JAVA_TOOL_OPTIONS`를 자동으로 읽습니다.

```bash
docker run --rm -p 8080:8080 \
  --env-file .env.local \
  -e SPRING_PROFILES_ACTIVE=local \
  -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0" \
  project-auth-server:local
```

Docker BuildKit이 비활성화된 환경이라면 아래처럼 켜서 빌드합니다.

```bash
DOCKER_BUILDKIT=1 docker build -t project-auth-server:local .
```

개발/운영 환경에서는 `.env.*` 파일을 이미지에 포함하지 않고, 런타임 환경 변수 또는 Kubernetes `ConfigMap`/`Secret`으로 주입하는 것을 기본 기준으로 합니다.
현재 베이스 이미지는 digest까지 고정해 두어 재현성을 높였고, 이후 보안 패치 주기에 맞춰 digest를 갱신하는 방식으로 운영하는 것을 권장합니다.

Actuator health endpoint는 아래 경로를 사용합니다.

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/livez`
- `/readyz`

## Dev CI/CD

이 저장소는 [`.github/workflows/dev-ci-cd.yml`](/home/donghyeon/dev/Project-Auth-Server/.github/workflows/dev-ci-cd.yml) 기준으로 dev CI/CD를 구성합니다.

- Pull Request to `develop`
  - `./gradlew test`
- Push to `develop`
  - `./gradlew test`
  - `ghcr.io/<owner>/project-auth-server:dev`
  - `ghcr.io/<owner>/project-auth-server:<short-sha>`
    두 태그로 이미지를 빌드/푸시
  - `k8s/dev/kustomization.yaml`의 `newTag`를 새 `<short-sha>`로 갱신
  - 같은 `develop` 브랜치에 manifest 변경 커밋 반영
  - Argo CD가 `develop`을 감시 중이면 새 태그를 sync

현재 dev 배포 선언은 아래 파일로 관리합니다.

- Kustomize: [k8s/dev/kustomization.yaml](/home/donghyeon/dev/Project-Auth-Server/k8s/dev/kustomization.yaml)
- Argo CD AppProject: [argocd/auth-dev-project.yaml](/home/donghyeon/dev/Project-Auth-Server/argocd/auth-dev-project.yaml)
- Argo CD Application: [argocd/dev-auth-server-application.yaml](/home/donghyeon/dev/Project-Auth-Server/argocd/dev-auth-server-application.yaml)

민감값은 Git에 직접 올리지 않고, [k8s/dev/secret.yaml](/home/donghyeon/dev/Project-Auth-Server/k8s/dev/secret.yaml)에 키 구조만 유지한 채 placeholder 값만 둡니다.
현재 dev 구성은 secret까지 Argo CD가 직접 생성하는 방식이 아니라, 실제 secret은 namespace에 사전 생성하고 Argo CD는 그 참조만 배포하는 방식입니다.

현재 workflow는 아래 기준으로 정리되어 있습니다.

- runner: `ubuntu-24.04`
- major tag 대신 명시적 action version 사용
- 현재 단일 아키 빌드만 하므로 QEMU 제거
- 배포 기준 태그는 `dev`가 아니라 `<short-sha>`
- `dev`는 편의용 moving tag로만 유지

dev namespace에서 먼저 필요한 secret은 아래 두 개입니다.

1. GHCR pull secret

```bash
kubectl create secret docker-registry ghcr-regcred \
  --namespace auth-dev \
  --docker-server=ghcr.io \
  --docker-username=<github-username> \
  --docker-password=<github-pat-or-ghcr-token>
```

2. auth-server secret

```bash
kubectl apply -f k8s/dev/namespace.yaml
kubectl apply -f k8s/dev/serviceaccount.yaml
kubectl apply -f k8s/dev/service.yaml
kubectl apply -f k8s/dev/configmap.yaml

cp k8s/dev/secret.yaml /tmp/auth-server-secret.yaml
# /tmp/auth-server-secret.yaml 안의 change-me 값을 실제 값으로 교체
kubectl apply -f /tmp/auth-server-secret.yaml
```

Argo CD는 클러스터에 별도 설치해야 합니다. 이 저장소는 Argo CD가 읽을 `Application` 선언만 함께 관리합니다.
적용 순서는 보통 `AppProject -> Application` 순서로 가져갑니다.

현재 dev namespace는 Pod Security Admission 기준으로 아래 라벨을 사용합니다.

- `enforce=baseline`
- `warn=restricted`
- `audit=restricted`

Deployment는 이에 맞춰 `runAsNonRoot`, `seccompProfile: RuntimeDefault`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, `startupProbe`, `livenessProbe`, `readinessProbe`를 포함합니다.

## Platform services for dev

auth-server가 실제로 기동되려면 `Postgres`, `Keycloak`, `Vault`가 먼저 필요합니다. dev 기준 공용 platform 서비스 manifest는 아래 경로로 관리합니다.

- Kustomize: [k8s/platform-dev/kustomization.yaml](/home/donghyeon/dev/Project-Auth-Server/k8s/platform-dev/kustomization.yaml)
- Argo CD Application: [argocd/dev-platform-application.yaml](/home/donghyeon/dev/Project-Auth-Server/argocd/dev-platform-application.yaml)

`platform-dev`는 별도 `AppProject`를 두지 않고 기존 [argocd/auth-dev-project.yaml](/home/donghyeon/dev/Project-Auth-Server/argocd/auth-dev-project.yaml)을 함께 사용합니다. 현재 단계에서는 같은 저장소/같은 dev 환경에서 auth-server와 공용 platform 서비스를 같이 관리하는 편이 단순하고 충분합니다.

현재 platform manifest는 아래 서비스 이름을 기준으로 앱과 연결됩니다.

- `postgres.platform.svc.cluster.local`
- `keycloak.platform.svc.cluster.local:8081`
- `vault.platform.svc.cluster.local`

민감값은 [k8s/platform-dev/secret.yaml](/home/donghyeon/dev/Project-Auth-Server/k8s/platform-dev/secret.yaml)에 placeholder만 두고, 실제 값으로 채운 뒤 namespace에 수동 적용하는 방식을 기준으로 합니다.

## Flyway 운영 기준

Flyway는 스키마 변경을 추적하기 위해 이번 브랜치에서 적용했습니다. 다만 운영 환경에서 애플리케이션 Pod가 스케일 아웃될 때마다 마이그레이션을 시도하게 두는 구조는 지양합니다.

이 프로젝트는 기본적으로 애플리케이션 시작 시 Flyway를 실행하지 않습니다.

- 기본값: `APP_PERSISTENCE_MIGRATION_RUN_ON_STARTUP=false`
- 일반 애플리케이션 Pod: `false`
- 마이그레이션 전용 Job/배포 단계: `true`

즉, 운영에서는 보통 아래 순서로 가져갑니다.

1. 마이그레이션 전용 Job 또는 CI/CD 단계가 DB에 먼저 붙어서 Flyway를 실행
2. 마이그레이션이 끝난 뒤 애플리케이션 Pod를 롤아웃

로컬에서 마이그레이션만 실행하고 싶다면, 같은 애플리케이션 이미지를 사용하더라도 아래처럼 별도 실행 컨텍스트로 분리하는 방식을 권장합니다.

```bash
APP_PERSISTENCE_MIGRATION_RUN_ON_STARTUP=true \
SPRING_MAIN_WEB_APPLICATION_TYPE=none \
./gradlew :bootstrap:bootRun
```

이 방식은 “앱 서버가 뜰 때마다 Flyway를 돈다”가 아니라, “DB에 대해 한 번만 실행하는 마이그레이션 프로세스”를 따로 두는 운영 형태를 연습하기 위한 기준입니다.

Kubernetes에서 같은 이미지를 마이그레이션 전용 Job으로 분리하는 예시는 `docs/k8s/auth-db-migration-job.yaml` 파일을 참고하면 됩니다.

## Keycloak 설정 기준

auth-server는 Google/GitHub와 직접 연결하지 않고 Keycloak과만 연결합니다. 따라서 소셜 로그인용 Client ID와 Secret은 Keycloak에 등록해야 합니다.

- auth-server에 넣는 값
  - Keycloak realm issuer
  - Keycloak client id
  - Keycloak client secret
- Keycloak에 넣는 값
  - Google OAuth Client ID / Secret
  - GitHub OAuth App Client ID / Secret

현재 로컬 기준으로 auth-server는 아래 Keycloak 설정을 기대합니다.

- realm: `project-auth`
- client id: `project-auth-server`
- client secret: `project-auth-server-secret`
- Google provider alias: `google`
- GitHub provider alias: `github`

자세한 순서는 `docs/keycloak/LOCAL_SETUP.md`를 참고하면 됩니다.

## API 문서

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

현재 브랜치에서는 회원가입 API와 로그인 API를 기준으로 문서를 확인할 수 있습니다.

추가로 아래 OAuth2 시작 API도 문서에서 확인할 수 있습니다.

- `GET /api/v1/auth/oauth2/keycloak/google`
- `GET /api/v1/auth/oauth2/keycloak/github`

위 두 API는 Keycloak 인증 화면으로 리다이렉트되며, 인증이 완료되면 auth-server가 내부 JWT를 다시 발급한 JSON 응답을 반환합니다.

## JWT 공개키 검증 기준

이 브랜치부터 auth-server는 대칭키가 아니라 RS256으로 JWT를 발급합니다. 그리고 키 회전을 위해 “현재 서명 키”와 “이전 검증 키”를 함께 관리할 수 있게 구성합니다.

- 로컬
  - 공개키/개인키를 따로 주지 않으면 시작 시 임시 RSA 키 쌍을 생성합니다.
  - 따라서 재시작 전후 토큰이 계속 유효해야 하는 상황이면 `.env.local`에 키를 직접 넣어야 합니다.
- `dev`, `prod`
  - `APP_SECURITY_JWT_ACTIVE_KEY_ID`로 현재 서명 키를 지정합니다.
  - `APP_SECURITY_JWT_KEYS_N_*` 묶음으로 현재 키와 이전 키를 함께 넣습니다.
  - 현재 활성 키는 private/public key를 모두 가져야 하고, 이전 키는 public key만 있어도 됩니다.
  - `APP_SECURITY_JWT_GENERATE_KEY_PAIR_ON_STARTUP=false`를 유지합니다.

공개 메타데이터는 아래 엔드포인트로 노출합니다.

- OpenID metadata: `http://localhost:8080/.well-known/openid-configuration`
- JWK Set: `http://localhost:8080/.well-known/jwks.json`

`api-server`나 `worker`가 Spring Security Resource Server로 검증하려면 보통 아래처럼 붙이면 됩니다.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080
```

위 설정을 사용하면 auth-server의 `issuer`와 `jwks_uri`를 기준으로 공개키를 자동 조회해 JWT 서명을 검증할 수 있습니다.

로컬에서 고정 키를 쓰고 싶으면 X.509 public key와 PKCS#8 private key를 Base64 또는 PEM 형식으로 환경 변수에 넣으면 됩니다. 관련 설정 위치는 아래 파일을 참고합니다.

- `.env.local`
- `.env.dev`
- `.env.prod`

OpenSSL로 키를 생성한 뒤 Base64로 환경 변수에 넣으려면 보통 아래 순서로 준비합니다.

```bash
openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in private_key.pem -out public_key.pem

base64 -w 0 private_key.pem
base64 -w 0 public_key.pem
```

활성 키는 `APP_SECURITY_JWT_ACTIVE_KEY_ID`로 고르고, 각 키의 material은 `APP_SECURITY_JWT_KEYS_N_PUBLIC_KEY`, `APP_SECURITY_JWT_KEYS_N_PRIVATE_KEY`로 넣습니다. 이전 키는 검증 전용이므로 private key 없이 public key만 남겨둘 수 있습니다.

예를 들어 키를 회전하는 시점에는 이런 식으로 운영합니다.

- `APP_SECURITY_JWT_ACTIVE_KEY_ID=auth-rsa-2`
- `APP_SECURITY_JWT_KEYS_0_KEY_ID=auth-rsa-1`
- `APP_SECURITY_JWT_KEYS_0_PUBLIC_KEY=...`
- `APP_SECURITY_JWT_KEYS_0_PRIVATE_KEY=` 비워둠
- `APP_SECURITY_JWT_KEYS_1_KEY_ID=auth-rsa-2`
- `APP_SECURITY_JWT_KEYS_1_PUBLIC_KEY=...`
- `APP_SECURITY_JWT_KEYS_1_PRIVATE_KEY=...`

이 상태에서는 새 토큰은 `auth-rsa-2`로 발급하고, 기존 `auth-rsa-1`로 서명된 토큰은 만료될 때까지 계속 검증할 수 있습니다.

현재 구현은 `ConfiguredJwtSigningKeySource`가 환경 변수 기반으로 키를 읽습니다. 이후 Secret 관리가 더 고도화되면 같은 `JwtSigningKeySource` 인터페이스를 구현하는 방식으로 Vault, AWS KMS, GCP KMS, HSM 연동으로 확장할 수 있습니다. 즉 이번 브랜치에서는 KMS/HSM 자체를 붙이기보다, 그 방향으로 갈 수 있도록 키 소스를 분리해 둔 상태입니다.

## Refresh Token 정책 기준

현재 auth-server는 refresh token을 아직 발급하지 않고, access token만 JSON 응답으로 반환합니다. 따라서 아래 항목은 이번 브랜치 범위에 포함하지 않습니다.

- refresh token 발급
- 재발급 API
- HttpOnly cookie 저장 전략
- refresh token 회전 및 폐기 정책

이유는 이 항목들이 단순 토큰 필드 추가 수준이 아니라, 아래 설계를 함께 요구하기 때문입니다.

- 저장 위치
  - DB, Redis, stateless token 중 무엇을 기준으로 할지
- 재발급 계약
  - API 응답 모델, 오류 코드, 만료/폐기 규칙
- 브라우저 보안 정책
  - `HttpOnly`, `Secure`, `SameSite`, CSRF 대응
- 로그아웃 및 세션 무효화 기준

이번 브랜치에서는 먼저 access token 서명 구조를 Vault Transit까지 확장 가능한 형태로 정리하고, refresh token은 별도 브랜치에서 다루는 것을 기본 방침으로 잡습니다. 순서는 아래처럼 가져갑니다.

1. Vault Transit 기반 access token 서명 구조 정리
2. refresh token 저장/재발급 정책 확정
3. cookie 전략과 브라우저 보안 정책 확정
4. 재발급 API와 로그아웃/폐기 흐름 구현

## Postman 사용 방법

`docs/postman/auth-core.postman_collection.json` 파일을 Postman에 import 하면 바로 테스트할 수 있습니다.

컬렉션에는 아래 요청이 포함되어 있습니다.

- 회원가입 성공
- 로그인 성공
- 회원가입 중복 이메일
- 로그인 인증 실패
- 회원가입 요청값 검증 실패

기본 변수는 `baseUrl=http://localhost:8080` 으로 설정되어 있습니다.

소셜 로그인은 브라우저 리다이렉트 기반이므로 Postman보다 브라우저에서 테스트하는 편이 맞습니다.

## 테스트

```bash
./gradlew test
```

테스트 범위는 다음을 포함합니다.

- 회원가입 유스케이스 단위 테스트
- 로그인 유스케이스 단위 테스트
- OAuth2 로그인 유스케이스 단위 테스트
- H2 기반 사용자 영속성 통합 테스트
- OAuth2 시작 컨트롤러 테스트
- 로그인 컨트롤러 테스트
- 회원가입 컨트롤러 테스트
- Swagger OpenAPI 노출 통합 테스트
