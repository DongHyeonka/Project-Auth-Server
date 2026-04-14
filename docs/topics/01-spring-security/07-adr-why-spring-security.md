# ADR-007: Spring Security를 OAuth2 브라우저 인증 진입 계층으로 사용한다

## Status

Accepted

## Date

2026-04-09

| 항목 | 값 |
|------|-----|
| Author | @DongHyeonka |
| Reviewer | — |
| Last Reviewed | 2026-04-09 |

## Context

이 프로젝트는 아래 요구사항을 동시에 만족해야 합니다.

- 브라우저 기준 로그인 진입점을 `/login`으로 제공해야 한다.
- Keycloak broker를 통한 Google/GitHub 소셜 로그인 분기가 필요하다.
- OAuth2 callback 이후 사용자 조회/등록과 JWT 발급은 우리 애플리케이션 유스케이스로 이어져야 한다.
- 로컬 이메일/비밀번호 로그인도 같은 서버에서 제공해야 한다.
- 토큰 발급은 auth-server가 직접 하되, signer는 로컬 RSA와 Vault Transit 사이에서 교체 가능해야 한다.

직접 filter와 callback 로직을 구현할 수도 있지만, authorization request 저장, callback 처리, 인증 객체 구성, failure handling 같은 표준 보안 프로토콜 코드를 직접 관리하는 부담이 큽니다.  
반대로 Keycloak이나 framework에 로그인 후처리와 토큰 정책까지 과하게 위임하면 계정 충돌 정책, 사용자 등록, JWT claim 구성을 우리 코드가 통제하기 어려워집니다.

## Decision Drivers

- redirect/callback 프로토콜 구현 리스크를 줄일 것
- `/login` 커스텀 로그인 허브와 자연스럽게 연결될 것
- callback 이후 비즈니스 후처리를 application service로 분리할 것
- 로컬 로그인과 OAuth2 로그인을 같은 도메인 정책 아래에서 관리할 것
- JWT signer와 key source를 운영 환경에 따라 교체 가능하게 유지할 것

## Considered Options

### Option 1: Servlet filter와 OAuth2 redirect/callback을 직접 구현한다

- 장점:
  - Spring Security 의존을 줄일 수 있습니다.
  - 모든 흐름이 애플리케이션 코드 안에 명시적으로 드러납니다.
- 단점:
  - authorization request 저장, callback 검증, `SecurityContext` 구성, 예외 처리까지 직접 책임져야 합니다.
  - 표준 프로토콜 구현 실수의 위험이 큽니다.
- 트레이드오프:
  - 제어권은 높아지지만, 프로토콜 안전성과 유지보수 비용이 크게 악화됩니다.

### Option 2: Spring Security OAuth2 Client를 사용하고, 도메인 로직은 application service에 둔다

- 장점:
  - 표준 OAuth2/OIDC 프로토콜 처리를 framework에 맡길 수 있습니다.
  - callback 이후 사용자 조회/등록과 JWT 발급은 유스케이스로 독립 유지할 수 있습니다.
  - `/login` 커스텀 페이지, success/failure handler, resolver 확장 포인트를 자연스럽게 조합할 수 있습니다.
- 단점:
  - Spring Security 기본 동작과 세션 모델을 이해해야 합니다.
  - callback과 `/complete`가 분리되어 처음 보면 흐름이 어렵습니다.
- 트레이드오프:
  - 프로토콜 안정성과 도메인 통제권 사이의 균형이 가장 좋습니다.

### Option 3: Keycloak에 더 많은 로직을 위임하고 애플리케이션은 거의 프록시처럼 만든다

- 장점:
  - 애플리케이션 코드가 더 단순해질 수 있습니다.
  - provider 연동 복잡성을 Keycloak에 더 많이 밀어 넣을 수 있습니다.
- 단점:
  - 사용자 등록 정책, 계정 충돌 정책, JWT claim 정책, signer 전략을 우리 코드로 통제하기 어렵습니다.
  - auth-server가 도메인 정책의 소유자 역할을 하기 어려워집니다.
- 트레이드오프:
  - 운영 편의성은 일부 좋아질 수 있지만, 현재 프로젝트가 원하는 정책 소유권을 잃습니다.

## Decision

현재 프로젝트는 **Option 2**를 선택한다.  
즉 Spring Security는 브라우저 로그인 진입과 OAuth2 login 프로토콜 처리를 담당하고, 애플리케이션 서비스는 로컬 로그인, 소셜 사용자 등록, 계정 충돌 판단, JWT 발급을 담당한다.

## Consequences

### 긍정적 결과

- 표준 OAuth2/OIDC 프로토콜 처리를 재구현하지 않아도 됩니다.
- `/login` 커스텀 페이지, `kc_idp_hint`, success/failure handler 같은 프로젝트 특화 요구사항을 framework 확장 포인트로 안전하게 얹을 수 있습니다.
- 로컬 로그인과 OAuth2 로그인 후처리를 동일한 application service 경계 안에서 유지할 수 있습니다.
- JWT signer를 로컬 RSA와 Vault Transit 사이에서 교체할 수 있습니다.

### 부정적 결과

- Spring Security 기본 동작을 이해하지 못하면 callback 이후 흐름이 블랙박스처럼 보일 수 있습니다.
- OAuth2 로그인은 세션에 의존하므로 완전 stateless 구조가 아닙니다.
- `authorizedClientRegistrationId` naming 규칙과 provider 추론 로직이 결합돼 있습니다.
- 현재 구조는 인증 중심이며, 인가(RBAC) 체계가 들어오면 추가 설계가 필요합니다.

### 위험 완화

- `/login`, `/oauth2/authorization/*`, `/login/oauth2/code/*`, `/api/v1/auth/oauth2/complete`의 책임 경계를 문서화한다.
- 로그인 페이지, discovery, OAuth2 controller 흐름을 테스트로 계속 검증한다.
- provider 추론 규칙이 더 복잡해지면 naming convention 대신 명시적 매핑으로 전환한다.
- RBAC, resource server, refresh token은 별도 ADR 또는 RFC로 확장한다.
