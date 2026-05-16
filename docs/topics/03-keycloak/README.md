# Keycloak

Keycloak 을 OAuth2 / OIDC Provider 겸 Identity Broker 로 두고 auth-server 를 ResourceServer 로 축소한 구조의 결정 기록과 설계 문서.

## 현재 프로젝트 맥락

- auth-server 는 Keycloak realm 이 발급한 access token 을 ResourceServer 로 검증한다.
- 로그인, OAuth2 broker, token 발급, issuer / JWKS 공개 책임은 Keycloak 이 가진다.
- auth-server 는 검증된 claim 을 바탕으로 내부 사용자를 식별하고, 처음 보는 Keycloak `sub` 는 email 충돌 검사 후 내부 사용자로 자동 등록한다.

## 문서

| 문서 | 내용 |
|------|------|
| [01-architecture.md](./01-architecture.md) | Keycloak 중심 인증 구조 + ResourceServer 검증 체인 + JWKS 캐시 / issuer-uri 운영 |
| [02-adr-keycloak-resource-server.md](./02-adr-keycloak-resource-server.md) | 인증 주체를 Keycloak 으로 이전한 ADR-002 |
| [03-claim-role-design.md](./03-claim-role-design.md) | Keycloak claim / role 설계 + sample JWT payload |
| [04-adr-token-ownership-cleanup.md](./04-adr-token-ownership-cleanup.md) | 자체 JWT / Vault Transit / 로컬 회원가입 일괄 제거 ADR-004 |
| [05-adr-keycloak-user-auto-registration.md](./05-adr-keycloak-user-auto-registration.md) | Keycloak 인증 사용자 내부 자동 등록 ADR-005 |

## 역할 분리

- 심화 설계 문서: 이 폴더
- 로컬 설정 절차: [docs/development/keycloak/LOCAL_SETUP.md](../../development/keycloak/LOCAL_SETUP.md)
- 로컬 realm import 자산: [docs/development/keycloak/realm/project-auth-realm-local.json](../../development/keycloak/realm/project-auth-realm-local.json)
