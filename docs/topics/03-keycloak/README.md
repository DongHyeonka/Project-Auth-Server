# Keycloak

## 개요

이 주제는 Keycloak을 OAuth2/OIDC Provider이자 Identity Broker로 사용하는 이유와, 현재 프로젝트가 Keycloak을 통해 소셜 로그인 흐름을 단순화하는 방식을 정리합니다.

## 현재 프로젝트 맥락

- auth-server는 Keycloak realm의 OIDC client 정보를 사용합니다.
- Google, GitHub 로그인은 Keycloak의 broker 기능을 통해 진입합니다.
- `kc_idp_hint`와 registration id 구성이 애플리케이션 라우트와 맞물립니다.

## 현재 문서

| 문서 | 내용 |
|------|------|
| [01-architecture.md](./01-architecture.md) | 현재 프로젝트의 Keycloak 배치와 broker 흐름 개요 |

## 역할 분리

- 심화 문서: 이 폴더
- 로컬 설정 런북: [docs/development/keycloak/LOCAL_SETUP.md](../../development/keycloak/LOCAL_SETUP.md)
- 로컬 realm import 자산: [docs/development/keycloak/realm/project-auth-realm-local.json](../../development/keycloak/realm/project-auth-realm-local.json)

## 다음에 확장할 문서

- `02-broker-flow.md`
- `03-practice.md`
- `04-troubleshooting.md`
- `05-adr-why-keycloak.md`
