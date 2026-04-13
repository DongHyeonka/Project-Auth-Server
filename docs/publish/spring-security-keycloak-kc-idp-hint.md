<!-- publish: velog -->

# Keycloak broker에서 `kc_idp_hint`를 왜 붙여야 할까

> Google과 GitHub 로그인 버튼은 둘 다 같은 Keycloak client로 시작했지만, 실제로 어느 provider로 보낼지는 `kc_idp_hint`가 결정하고 있었습니다. 이 값을 이해하지 못하면 broker 구조가 한 번에 읽히지 않습니다.

## 이 글을 쓰게 된 배경

- 인증 서버에서 Google과 GitHub 로그인을 직접 붙이지 않고 Keycloak broker를 통해 우회하는 구조를 사용하고 있었습니다.
- 그런데 애플리케이션 코드에는 provider가 두 개처럼 보이는데, 설정을 보면 실제 client registration은 같은 Keycloak realm을 바라보고 있었습니다.
- "그럼 Google 버튼과 GitHub 버튼은 정확히 어디서 갈라지지?"라는 질문이 자연스럽게 나왔습니다.

## 문제 상황

- `/api/v1/auth/oauth2/keycloak/google`과 `/api/v1/auth/oauth2/keycloak/github` 두 endpoint가 있습니다.
- 둘 다 결국 Spring Security OAuth2 authorization endpoint로 들어갑니다.
- 그런데 Keycloak 입장에서는 같은 realm, 같은 client를 보고 있으니 어떤 upstream provider로 보낼지 별도 힌트가 없으면 애매해집니다.

## 처음에 했던 가정

- registration id만 다르면 Keycloak이 알아서 Google/GitHub를 구분할 거라고 생각했습니다.
- 또는 provider alias가 client id와 자동으로 연결될 거라고 막연히 기대했습니다.

## 삽질 과정

### 시도 1

- controller redirect 경로가 다르니 그 차이만으로 충분하다고 생각했습니다.
- 하지만 Keycloak broker는 결국 자신에게 들어온 authorization request 안에서 어떤 provider를 쓸지 판단할 정보가 필요했습니다.

### 시도 2

- registration id naming만 맞추면 모든 게 자동으로 해결될 거라고 봤습니다.
- 그러나 registration id는 Spring Security 내부 구분값이고, Keycloak이 실제로 보는 provider alias와는 별도 개념이었습니다.

### 시도 3

- Keycloak 설정만 보면 된다고 생각했습니다.
- 실제로는 애플리케이션이 authorization request를 만들 때 `kc_idp_hint`를 추가해 주고 있었고, 이 값이 Keycloak alias와 정확히 맞아야 했습니다.

## 원인

broker 구조에서는 "애플리케이션이 바라보는 provider"와 "Keycloak이 upstream으로 연결하는 provider"가 분리됩니다.

- 애플리케이션 관점:
  - Keycloak OIDC client로 로그인한다.
- Keycloak 관점:
  - Google 또는 GitHub 같은 upstream identity provider로 사용자를 보낸다.

이 두 층을 연결하는 값이 `kc_idp_hint`입니다.

즉 authorization request에 아래 같은 힌트가 붙습니다.

```text
kc_idp_hint=google
kc_idp_hint=github
```

Keycloak은 이 값을 보고 미리 지정된 provider alias로 로그인 흐름을 보냅니다.

## 해결

```text
1. 소셜 로그인 시작 endpoint를 provider별로 분리한다.
2. Spring Security authorization request resolver를 감싼다.
3. registration id에 따라 kc_idp_hint를 추가한다.
4. Keycloak identity provider alias를 hint 값과 정확히 맞춘다.
```

이렇게 정리하면 애플리케이션은 여전히 Keycloak 하나만 OIDC provider로 다루면서도, 사용자는 Google과 GitHub 버튼을 별도 진입점처럼 사용할 수 있습니다.

## 무엇을 배웠나

- broker 구조에서는 registration id와 실제 upstream provider alias를 구분해서 봐야 합니다.
- "버튼이 두 개다"와 "OIDC provider가 두 개다"는 같은 말이 아닙니다.
- Keycloak broker를 쓸 때는 authorization request에 어떤 힌트가 추가되는지 보는 것이 가장 빠른 디버깅 포인트입니다.

## 글을 마치며

처음에는 `kc_idp_hint`가 사소한 추가 파라미터처럼 보였지만, 실제로는 broker 구조를 이해하는 핵심 열쇠였습니다.  
이 값을 기준으로 보면 앱, Spring Security, Keycloak, 외부 provider의 역할이 훨씬 명확해집니다.

## 참고 자료

- 원문 문서: [docs/topics/01-spring-security/03-authentication-flow.md](../topics/01-spring-security/03-authentication-flow.md)
- 원문 문서: [docs/topics/03-keycloak/README.md](../topics/03-keycloak/README.md)
- 관련 코드: `KeycloakIdpHintAuthorizationRequestResolver`, `AuthOAuth2Controller`
- 공식 문서:
  - Keycloak Identity Brokering
  - Spring Security OAuth2 Client

---
> 🔗 이 내용의 기술 선택 근거와 전체 문서는
> 이 저장소의 `docs/topics/01-spring-security/`와 `docs/topics/03-keycloak/`에서 이어서 볼 수 있습니다.
