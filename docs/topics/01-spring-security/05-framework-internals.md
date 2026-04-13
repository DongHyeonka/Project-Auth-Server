# Spring Security 내부 타입 해설

## Why

실무에서 많이 받는 질문은 "우리 코드가 무엇을 하느냐"보다 "Spring Security 내부 객체가 정확히 뭐냐"인 경우가 많습니다.  
이 문서는 현재 프로젝트에 실제로 등장하는 타입을 기준으로 framework 내부 개념을 설명합니다.

## What

현재 코드에서 실제로 만나는 핵심 타입은 아래와 같습니다.

| 타입 | 현재 프로젝트에서 등장하는 위치 | 의미 |
|------|------------------------------|------|
| `SecurityFilterChain` | `OAuth2SecurityConfiguration` | HTTP 보안 규칙과 OAuth2 login 구성을 담는 최상위 진입점 |
| `HttpSecurity` | `OAuth2SecurityConfiguration` | filter chain을 빌드하는 DSL |
| `Authentication` | `AuthOAuth2Controller.completeOAuthLogin()` | 현재 인증된 사용자를 나타내는 추상 타입 |
| `OAuth2AuthenticationToken` | `OAuth2AuthenticationCommandMapper` | OAuth2 client 로그인 결과를 담는 구현체 |
| `OidcUser` | `OAuth2AuthenticationCommandMapper` | OIDC id token / user info 기반 사용자 표현 |
| `GrantedAuthority` | 테스트와 framework 내부 | 권한/role 표현. 현재 프로젝트는 거의 사용하지 않음 |
| `OAuth2AuthorizationRequestResolver` | `KeycloakIdpHintAuthorizationRequestResolver` | authorization redirect 직전 요청을 커스터마이징 |
| `AuthenticationSuccessHandler` | `OAuth2LoginSuccessHandler` | 인증 성공 후 후처리 |
| `AuthenticationFailureHandler` | `OAuth2LoginFailureHandler` | 인증 실패 후 후처리 |

## How

### Q1. `Authentication`은 왜 controller parameter로 바로 들어오나요?

Spring MVC는 현재 요청의 `SecurityContext`를 보고 인증 객체를 메서드 파라미터로 주입할 수 있습니다.  
현재 프로젝트에서는 OAuth2 callback 이후 success handler가 `/api/v1/auth/oauth2/complete`로 이동시키고, 그 시점에 인증 상태가 유지되어 있기 때문에 controller가 `Authentication`을 받을 수 있습니다.

### Q2. 이 프로젝트에서 `principal`의 실제 타입은 무엇인가요?

현재 OAuth2 로그인 흐름에서는 `Authentication`의 실제 구현이 `OAuth2AuthenticationToken`이고, 그 안의 principal은 `OidcUser`입니다.  
좀 더 구체적으로는 테스트에서 `DefaultOidcUser`를 사용하고 있으며, 실제 런타임도 OIDC scope(`openid`)가 포함되어 있으므로 OIDC user 모델이 들어옵니다.

즉 "principal이 뭐냐"는 질문에는 아래처럼 답하면 됩니다.

> 현재 프로젝트의 OAuth2 principal은 일반 `java.security.Principal` 커스텀 구현이 아니라, Spring Security가 만든 `OidcUser` 계열 객체다.

### Q3. `Principal` 같은 인터페이스에 `hashCode()`나 `equals()` 이야기가 왜 나오나요?

이건 아주 좋은 질문입니다. 핵심은 **인증 주체는 단순 문자열이 아니라 비교 가능한 identity 객체**라는 점입니다.

- Java 인터페이스는 `Object`에서 오는 메서드(`equals`, `hashCode`, `toString`)에 대한 계약을 문서화할 수 있습니다.
- 인증 객체는 Set/Map의 key, 세션 복원 비교, 테스트 assertion, SecurityContext 변경 감지 같은 곳에서 비교됩니다.
- 따라서 principal 구현체는 "같은 사용자인가"를 안정적으로 판단할 수 있어야 하고, 그때 `equals`/`hashCode` 계약이 중요합니다.

현재 프로젝트는 `Principal` 구현체를 직접 만들지 않습니다.  
대신 Spring Security가 제공하는 `OidcUser`, `OAuth2AuthenticationToken`, `DefaultOidcUser` 같은 구현체를 소비합니다.

즉 이 질문에 대한 현재 프로젝트 기준 답변은 아래와 같습니다.

> `hashCode()`가 필요한 이유는 principal이 단순 출력용 객체가 아니라, 인증 상태 비교와 컬렉션 동작에 쓰이는 identity 객체이기 때문이다. 다만 우리 프로젝트는 그 구현을 직접 작성하지 않고 framework 구현을 사용한다.

### Q4. `Authentication`과 `Principal`은 뭐가 다른가요?

- `Principal`: "누구인가"에 더 가깝습니다.
- `Authentication`: "누구인가 + 어떤 방식으로 인증되었는가 + 어떤 권한이 있는가"를 함께 담습니다.

현재 프로젝트에서 controller가 `Authentication`을 받는 이유는 principal만으로는 registration id, authorities 같은 부가 정보를 잃을 수 있기 때문입니다.

### Q5. `OidcUser`를 쓰는 이유는 뭔가요?

현재 Keycloak client registration scope에 아래 값이 포함되어 있습니다.

- `openid`
- `profile`
- `email`

따라서 로그인 결과는 단순 OAuth2 user보다 OIDC user로 다루는 것이 자연스럽습니다.  
실제로 mapper는 `OidcUser.getSubject()`, `getEmail()`, `getFullName()`, `getPreferredUsername()`를 사용합니다.

### Q6. `GrantedAuthority`는 어디서 오고 왜 거의 안 쓰나요?

Spring Security는 인증이 완료되면 사용자에게 `GrantedAuthority` 목록을 붙입니다.  
테스트에서도 `ROLE_USER`를 가진 `DefaultOidcUser`를 만듭니다.

하지만 현재 프로젝트는 권한 기반 인가를 아직 구현하지 않았기 때문에 이 값을 business decision에 사용하지 않습니다.  
즉 authority는 "있지만 지금은 거의 소비하지 않는 정보"입니다.

### Q7. 왜 custom filter를 만들지 않고 resolver/handler만 바꾸나요?

현재 요구사항은 OAuth2 프로토콜 자체를 바꾸는 것이 아니라, 표준 흐름의 일부 지점만 프로젝트에 맞게 바꾸는 것입니다.

- redirect 직전: `kc_idp_hint` 추가
- 성공 직후: `/api/v1/auth/oauth2/complete`로 이동
- 실패 직후: JSON 에러로 변환

이 정도 요구사항이라면 전체 filter를 새로 만들기보다 framework extension point를 쓰는 편이 훨씬 안전합니다.

### Q8. provider는 왜 `GOOGLE`, `GITHUB`로 기록되나요?

현재 애플리케이션 도메인 enum은 `LOCAL`, `GOOGLE`, `GITHUB`입니다.  
그래서 mapper는 `authorizedClientRegistrationId`를 보고 Google/GitHub를 추론해 `OAuthLoginCommand`로 넘깁니다.

중요한 점은 **로그인 프로토콜의 직접 provider는 Keycloak이지만, 도메인에서 저장하는 provider는 최종 upstream provider**라는 점입니다.

즉 아래처럼 구분해서 답해야 합니다.

- Spring Security client 관점: Keycloak에 로그인한다.
- 도메인 사용자 관점: Google 또는 GitHub 소셜 계정으로 가입/로그인한다.

### Q9. 왜 access token을 쓰는데도 세션이 필요한가요?

OAuth2 authorization code flow는 redirect 기반의 다단계 handshake이기 때문에 callback 완료 전까지는 세션이 필요합니다.  
즉 "JWT를 쓴다"와 "서버가 절대 stateless다"는 같은 말이 아닙니다.

세션이 어떤 지점을 연결하는지는 [03-authentication-flow.md 8절](./03-authentication-flow.md#8-세션이-왜-필요한가)에서 상세히 설명합니다.

## Result

이 문서를 읽고 나면 아래 질문에 답할 수 있어야 합니다.

- `Authentication`, `Principal`, `OidcUser`는 서로 어떻게 다른가?
- 왜 `hashCode()` 같은 메서드가 인증 객체 설명에서 중요하게 언급되는가?
- 왜 현재 프로젝트는 authority를 갖고도 RBAC를 하지 않는가?
- 왜 custom filter를 만들지 않고 resolver/handler만 바꿨는가?
