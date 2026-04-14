# Spring Security 문서

## 개요

이 폴더는 `Project-Auth-Server`에 실제로 적용된 Spring Security와 주변 보안 구성을 코드 기준으로 정리한 문서 모음입니다.  
목표는 단순 사용법 요약이 아니라, "현재 인증 서버에서 어떤 보안 기능이 어디에 있고 왜 그렇게 동작하는가"를 끝까지 설명할 수 있는 기준 문서를 만드는 것입니다.

## 학습 배경

- 이 프로젝트는 OAuth2 redirect/callback, 세션 유지, 로컬 로그인, JWT 발급, JWK 공개를 함께 다룹니다.
- 따라서 "Spring Security를 어디까지 사용하고 어디서부터 우리 코드가 맡는가"를 명확히 설명할 필요가 있습니다.
- 나중에 누가 와서 `principal`, `Authentication`, `kc_idp_hint`, `issuer`, `kid`, Vault signer 같은 질문을 하더라도 코드 근거로 답할 수 있어야 합니다.

## 현재 프로젝트 맥락

현재 프로젝트의 보안은 크게 세 층으로 나뉩니다.

1. Spring Security가 HTTP 진입점, OAuth2 redirect/callback, 세션 기반 인증 상태를 처리합니다.
2. 애플리케이션 서비스가 로컬 로그인, 소셜 로그인 사용자 등록, JWT 발급을 처리합니다.
3. 인프라 계층이 BCrypt, RSA/Vault Transit 서명, JWK 노출, 사용자 영속화를 담당합니다.

아래 표는 지금 코드베이스에 실제로 적용된 security inventory의 축약본입니다.

| 영역 | 현재 상태 | 핵심 설명 |
|------|-----------|-----------|
| HTTP 보안 진입점 | 적용 | `SecurityFilterChain`에서 공개/보호 경로를 분리합니다. |
| OAuth2 Client 로그인 | 적용 | Keycloak을 OIDC Provider 겸 broker로 사용합니다. |
| Keycloak provider 분기 | 적용 | `kc_idp_hint`로 Google/GitHub 브로커 분기를 제어합니다. |
| 로컬 로그인 | 적용 | 서비스 계층에서 이메일/비밀번호를 검증하고 JWT를 발급합니다. |
| 토큰 발급 / 키 관리 | 적용 | RS256 access token을 발급하고 로컬 RSA 또는 Vault Transit signer를 사용합니다. |
| OIDC discovery / JWK 공개 | 적용 | `/.well-known/openid-configuration`, `/.well-known/jwks.json`를 노출합니다. |
| 역할 기반 인가 | 미적용 | 현재는 `authenticated()` 수준까지만 적용되어 있습니다. |

## 문서 구조

| 문서 | 내용 |
|------|------|
| [01-architecture.md](./01-architecture.md) | 전체 구조, 책임 분리, 공개/보호 경로, Spring Security가 개입하는 지점 |
| [02-security-inventory.md](./02-security-inventory.md) | 적용된 security 기능 전체 목록과 "무엇이 있고 무엇이 없는가" |
| [03-authentication-flow.md](./03-authentication-flow.md) | 로컬 로그인과 OAuth2 로그인 요청/응답 흐름, 세션과 callback 처리 |
| [04-token-password-and-discovery.md](./04-token-password-and-discovery.md) | BCrypt, JWT claim, RSA/Vault 서명, JWK/OIDC discovery |
| [05-framework-internals.md](./05-framework-internals.md) | `Authentication`, `Principal`, `OidcUser`, `GrantedAuthority`, `hashCode()` 같은 내부 개념 |
| [06-troubleshooting.md](./06-troubleshooting.md) | 운영/개발 중 자주 만나는 보안 이슈와 원인, 수정 포인트 |
| [07-adr-why-spring-security.md](./07-adr-why-spring-security.md) | 왜 Spring Security를 선택했고 다른 대안은 무엇이었는가 |
| [08-question-bank.md](./08-question-bank.md) | 실제 질문을 바로 꺼내 답할 수 있도록 정리한 세부 질문집 |

## 핵심 키워드

`SecurityFilterChain` · `OAuth2 Login` · `Keycloak Broker` · `Authentication` · `OidcUser` · `BCrypt` · `RS256` · `JWK` · `Vault Transit`

## 읽는 순서

1. [01-architecture.md](./01-architecture.md)
2. [02-security-inventory.md](./02-security-inventory.md)
3. [03-authentication-flow.md](./03-authentication-flow.md)
4. [04-token-password-and-discovery.md](./04-token-password-and-discovery.md)
5. [05-framework-internals.md](./05-framework-internals.md)
6. [06-troubleshooting.md](./06-troubleshooting.md)
7. [07-adr-why-spring-security.md](./07-adr-why-spring-security.md)
8. [08-question-bank.md](./08-question-bank.md)

## 관련 문서

- Keycloak 심화: [../03-keycloak/README.md](../03-keycloak/README.md)
- Vault 심화: [../05-vault/README.md](../05-vault/README.md)
- Keycloak 로컬 런북: [../../development/keycloak/LOCAL_SETUP.md](../../development/keycloak/LOCAL_SETUP.md)
- Vault 로컬 런북: [../../development/vault-local-setup.md](../../development/vault-local-setup.md)
- 공개용 초안: [../../publish/spring-security-why-oauth2-login-still-needs-session.md](../../publish/spring-security-why-oauth2-login-still-needs-session.md)
- 공개용 초안: [../../publish/spring-security-keycloak-kc-idp-hint.md](../../publish/spring-security-keycloak-kc-idp-hint.md)
