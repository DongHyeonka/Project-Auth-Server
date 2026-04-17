# Keycloak

## 개요

이 주제는 Keycloak을 OAuth2/OIDC Provider이자 Identity Broker로 사용하는 이유와, 현재 프로젝트가 인증 주체를 Keycloak으로 옮기고 auth-server를 Resource Server로 축소한 방식을 정리합니다.

## 현재 프로젝트 맥락

- auth-server는 Keycloak realm이 발급한 access token을 Resource Server로 검증합니다.
- 로그인, OAuth2 broker, token 발급, issuer/JWKS 공개 책임은 Keycloak이 가집니다.
- auth-server는 검증된 claim을 바탕으로 내부 사용자 식별자를 동기화합니다.

## 현재 문서

| 문서 | 내용 |
|------|------|
| [01-architecture.md](./01-architecture.md) | Keycloak 중심 인증 구조와 Resource Server 경계 |
| [02-adr-keycloak-resource-server.md](./02-adr-keycloak-resource-server.md) | 인증 주체를 Keycloak으로 이전한 ADR |
| [03-claim-role-design.md](./03-claim-role-design.md) | Keycloak claim/role 설계 |
| [04-token-ownership-inventory.md](./04-token-ownership-inventory.md) | 자체 JWT/Vault Transit 사용처 정리와 제거 결과 |

## 역할 분리

- 심화 문서: 이 폴더
- 로컬 설정 런북: [docs/development/keycloak/LOCAL_SETUP.md](../../development/keycloak/LOCAL_SETUP.md)
- 로컬 realm import 자산: [docs/development/keycloak/realm/project-auth-realm-local.json](../../development/keycloak/realm/project-auth-realm-local.json)
