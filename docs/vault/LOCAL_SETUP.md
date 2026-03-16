# Vault Transit 로컬 설정

## 기본 전제

- 루트 `docker-compose.yml`에 `vault`, `vault-init` 서비스가 포함되어 있습니다.
- 현재 단계는 Vault Transit 개념과 로컬 서명 흐름을 익히는 단계입니다.
- 아직 auth-server가 Vault를 실제 signer로 사용하지 않아도, transit 엔진과 키를 먼저 준비해 둘 수 있습니다.

## 1. 로컬 인프라 실행

`.env.local`을 확인한 뒤 아래 명령으로 실행합니다.

```bash
docker compose --env-file .env.local up -d
```

기본 포트는 아래와 같습니다.

- Vault UI/API: `http://localhost:8200`
- Root token: `.env.local`의 `VAULT_DEV_ROOT_TOKEN_ID`

## 2. 자동 초기화되는 항목

`vault-init` 서비스가 한 번 실행되면서 아래 작업을 수행합니다.

- `transit` secrets engine 활성화
- transit signing key 생성

기본 key 이름은 아래 환경 변수 기준입니다.

- `VAULT_TRANSIT_KEY_NAME=project-auth-jwt`

## 3. 상태 확인

Vault UI에 접속한 뒤 `.env.local`의 root token으로 로그인합니다.

또는 CLI로 확인할 수 있습니다.

```bash
docker compose --env-file .env.local logs vault-init
```

```bash
docker compose --env-file .env.local exec vault \
  vault read transit/keys/project-auth-jwt
```

## 4. auth-server 연동 기준

`.env.local` 기준으로 로컬 auth-server는 아래 설정이 켜져 있으면 Vault Transit signer를 사용합니다.

- `APP_SECURITY_JWT_VAULT_ENABLED=true`
- `APP_SECURITY_JWT_VAULT_ADDRESS=http://localhost:8200`
- `APP_SECURITY_JWT_VAULT_TOKEN=project-auth-root-token`
- `APP_SECURITY_JWT_VAULT_MOUNT_PATH=transit`
- `APP_SECURITY_JWT_VAULT_TRANSIT_KEY_NAME=project-auth-jwt`

즉 로그인 성공 시 auth-server는 로컬 private key로 직접 서명하지 않고, Vault `transit/sign/...` API에 서명을 위임합니다.

## 5. 현재 단계의 의미

이번 단계에서는 아래 두 가지를 먼저 확인합니다.

1. Vault가 로컬에서 정상 기동되는가
2. Transit key가 자동으로 준비되는가
3. auth-server가 해당 key의 public key를 읽고, 서명은 Transit API에 위임하는가

그 다음 단계에서 auth-server signer를 Transit API 호출 방식으로 교체합니다.
