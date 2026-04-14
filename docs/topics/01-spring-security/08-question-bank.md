# Spring Security 세부 질문집

## Why

이 문서는 "문서를 다 읽고 이해했다" 단계가 아니라, 실제로 질문을 받을 때 바로 답변할 수 있도록 만든 Q&A 기준서입니다.  
면접, 코드리뷰, 온보딩, 장애 대응, 문서 보강 논의에서 반복해서 나오는 질문을 현재 코드와 문서 기준으로 정리합니다.

## What

질문은 여섯 묶음으로 정리합니다.

1. 구조와 책임 경계
2. OAuth2 / Keycloak 흐름
3. Spring Security 내부 객체
4. 로컬 로그인과 비밀번호 보호
5. JWT / JWK / Vault
6. 현재 없는 기능과 확장 포인트

## How

### 1. 구조와 책임 경계

### Q1. 이 프로젝트에서 Spring Security는 정확히 어디까지 맡나요?

Spring Security는 HTTP 초입에서 공개/보호 경로를 나누고, OAuth2 authorization redirect와 callback 인증을 처리합니다.  
반면 로컬 로그인 검증, 사용자 등록, JWT 발급 자체는 애플리케이션 서비스가 맡습니다.  
즉 프로토콜 처리는 Spring Security, 도메인 정책은 애플리케이션이라는 경계입니다.

### Q2. 보안 진입점은 어디서 시작되나요?

핵심 진입점은 `bootstrap/.../OAuth2SecurityConfiguration.java`의 `SecurityFilterChain`입니다.  
여기서 `csrf`, `authorizeHttpRequests`, `oauth2Login`, 성공/실패 handler, authorization request resolver를 한 번에 조립합니다.

### Q3. 왜 로컬 로그인까지 Spring Security `AuthenticationProvider`로 안 했나요?

현재 프로젝트는 로그인도 명시적인 유스케이스로 다루기 위해 `LoginService`에서 직접 검증합니다.  
이 방식은 Spring Security 표준 provider 체인보다 자동화는 적지만, Clean Architecture 관점에서는 테스트와 책임 분리가 더 명확합니다.

### Q4. 현재 인가는 어느 수준까지 되어 있나요?

현재 인가는 "인증되었는가" 수준입니다.  
공개 경로를 제외한 나머지 요청은 `anyRequest().authenticated()`로 보호하지만, 역할 기반 RBAC는 없습니다.

### Q5. 현재 보안에서 가장 중요한 설계 포인트 한 줄로 뭐라고 말하면 되나요?

`Spring Security는 OAuth2/OIDC 기반 HTTP 인증 진입을 맡고, 애플리케이션 서비스는 사용자 검증과 JWT 발급을 맡으며, 인프라는 BCrypt와 RSA/Vault 서명을 담당한다`라고 정리하면 됩니다.

### 2. OAuth2 / Keycloak 흐름

### Q6. 왜 Google/GitHub를 직접 붙이지 않고 Keycloak을 끼웠나요?

애플리케이션이 여러 소셜 provider와 직접 프로토콜 차이를 맞추지 않도록 Keycloak broker를 중간에 둔 것입니다.  
auth-server는 Keycloak만 OIDC provider로 보고, Google/GitHub 분기는 `kc_idp_hint`로 위임합니다.

### Q7. OAuth2 로그인 시작 endpoint는 어디인가요?

사용자 친화적인 시작점은 아래 두 개입니다.

- `GET /api/v1/auth/oauth2/keycloak/google`
- `GET /api/v1/auth/oauth2/keycloak/github`

이 endpoint는 바로 Keycloak로 가지 않고, 먼저 `/oauth2/authorization/keycloak-google` 또는 `/oauth2/authorization/keycloak-github`로 redirect합니다.

### Q8. `kc_idp_hint`는 왜 필요한가요?

Keycloak broker에게 "이번 로그인은 어떤 upstream identity provider로 보낼지" 알려주기 위해 필요합니다.  
현재는 registration id가 Google이면 `google`, GitHub면 `github`를 추가합니다.

### Q9. `kc_idp_hint`는 어디서 붙나요?

`KeycloakIdpHintAuthorizationRequestResolver`가 Spring Security 기본 authorization request resolver를 감싼 뒤 `additionalParameters`에 넣습니다.  
즉 redirect 직전 customization 포인트에서 붙습니다.

### Q10. callback 이후 왜 바로 JWT를 응답하지 않고 `/api/v1/auth/oauth2/complete`로 한 번 더 가나요?

OAuth2 프로토콜 완료와 비즈니스 후처리를 분리하기 위해서입니다.  
callback은 Spring Security가 처리하고, 그 뒤 사용자 등록/조회와 JWT 발급은 일반 controller + application service 흐름으로 넘깁니다.

→ 상세 흐름: [03-authentication-flow.md 5절](./03-authentication-flow.md#5-callback-이후-왜-바로-jwt를-주지-않는가)

### Q11. 왜 `/api/v1/auth/oauth2/complete`는 `permitAll`이 아닌가요?

이 endpoint는 callback 이후 SecurityContext에 인증 상태가 있어야만 접근해야 하기 때문입니다.  
즉 "누구나 호출 가능한 마무리 endpoint"가 아니라, 이미 OAuth2 로그인에 성공한 사용자의 후속 처리 endpoint입니다.

→ 보호 경로 상세: [03-authentication-flow.md 6절](./03-authentication-flow.md#6-apiv1authoauth2complete가-보호-경로인-이유)

### Q12. JWT를 주는데 왜 세션이 필요한가요?

최종 결과는 JWT여도 OAuth2 authorization code flow는 redirect와 callback으로 이루어진 다단계 handshake입니다.  
현재 구조는 그 중간 상태를 Spring Security 기본 세션 모델에 기대고 있기 때문에 callback 완료 전까지는 세션이 필요합니다.

→ 세션이 연결하는 지점 상세: [03-authentication-flow.md 8절](./03-authentication-flow.md#8-세션이-왜-필요한가)

### Q13. Keycloak alias와 registration id는 같은 건가요?

아닙니다.

- registration id: Spring Security client registration 이름
- `kc_idp_hint`: Keycloak broker에게 넘기는 provider alias 힌트

현재 프로젝트는 `keycloak-google` 같은 registration id와 `google` 같은 Keycloak alias가 서로 연결되어 있습니다.

### 3. Spring Security 내부 객체

### Q14. controller에서 받는 `Authentication`의 실제 타입은 뭔가요?

현재 OAuth2 로그인 흐름에서는 `OAuth2AuthenticationToken`입니다.  
그 안의 principal은 `OidcUser` 계열 객체입니다.

→ 타입 계층 상세: [05-framework-internals.md Q2](./05-framework-internals.md#q2-이-프로젝트에서-principal의-실제-타입은-무엇인가요)

### Q15. `principal`과 `Authentication`은 뭐가 다른가요?

- `principal`: 누구인가
- `Authentication`: 누구인가 + 어떻게 인증됐는가 + 어떤 권한이 있는가

현재 프로젝트는 provider 추론과 authorities 같은 부가 정보가 필요하므로 controller에서 `Authentication`을 받습니다.

### Q16. 왜 `OidcUser`를 쓰나요?

client registration scope에 `openid`, `profile`, `email`이 포함되어 있기 때문입니다.  
그래서 로그인 결과를 OIDC user로 다루는 것이 자연스럽고, 실제 mapper도 `sub`, `email`, `name`, `preferred_username`를 읽습니다.

### Q17. `Principal`이나 사용자 객체에 `equals()` / `hashCode()`가 왜 중요하죠?

인증 객체는 단순 출력용 값이 아니라 identity 비교 대상이기 때문입니다.  
세션 복원, 컬렉션 key, SecurityContext 변경 감지, 테스트 비교 같은 상황에서 같은 사용자인지 일관되게 비교할 수 있어야 합니다.  
현재 프로젝트는 이 구현을 직접 쓰지 않고 Spring Security의 `OidcUser` 구현에 기대고 있습니다.

### Q18. `GrantedAuthority`는 있는데 왜 거의 안 쓰나요?

Spring Security는 인증 결과에 authority를 붙이지만, 현재 프로젝트는 역할 기반 인가를 구현하지 않았습니다.  
그래서 authority는 존재하지만 business decision에는 거의 사용되지 않습니다.

### Q19. 왜 custom filter를 만들지 않고 resolver/handler만 바꾸나요?

요구사항이 OAuth2 프로토콜 자체 변경이 아니라 표준 흐름 일부 customization이기 때문입니다.  
redirect 직전, 성공 직후, 실패 직후만 바꾸면 되므로 framework extension point를 사용하는 편이 안전합니다.

→ 확장 포인트 상세: [05-framework-internals.md Q7](./05-framework-internals.md#q7-왜-custom-filter를-만들지-않고-resolverhandler만-바꾸나요)

### 4. 로컬 로그인과 비밀번호 보호

### Q20. 로컬 로그인 흐름은 어떻게 되나요?

`AuthLoginController -> LoginService -> JpaUserRepositoryAdapter -> BcryptPasswordEncoderAdapter -> NimbusJwtTokenIssuerAdapter` 순서입니다.  
이메일 조회, provider 확인, BCrypt 비교를 통과해야 JWT가 발급됩니다.

### Q21. 왜 소셜 계정 이메일로 로컬 로그인하면 실패하나요?

`LoginService`는 사용자 provider가 `LOCAL`인지 먼저 확인합니다.  
즉 이메일이 같아도 인증 방식이 다른 계정은 로컬 비밀번호 로그인을 허용하지 않습니다.

### Q22. 비밀번호는 어디에서 검증하고 어디에서 해시하나요?

입력 길이와 공백은 DTO와 validator가 먼저 검사하고, 도메인 정책은 `UserPasswordPolicy`가 재검증합니다.  
실제 해시는 `BcryptPasswordEncoderAdapter`가 BCrypt로 생성합니다.

### Q23. 왜 BCrypt를 선택했나요?

Spring Security가 검증된 `PasswordEncoder` 구현을 제공하고, 느린 해시 특성 덕분에 비밀번호 저장에 적합하기 때문입니다.  
현재 프로젝트는 salt 관리까지 직접 하지 않고 BCrypt 구현에 맡깁니다.

### Q24. 회원가입에서 보안 관점으로 중요한 점은 뭔가요?

- raw password는 저장 전에 반드시 해시합니다.
- email 중복을 먼저 검사합니다.
- 도메인 모델이 `LOCAL` 사용자와 소셜 사용자를 다르게 복원합니다.

즉 입력 검증, 중복 방지, 저장 형식이 분리되어 있습니다.

### 5. JWT / JWK / Vault

### Q25. JWT는 어디서 발급하나요?

실제 발급기는 `NimbusJwtTokenIssuerAdapter`입니다.  
로컬 로그인과 OAuth2 로그인 모두 이 발급기를 통해 access token을 만듭니다.

### Q26. JWT에 어떤 claim이 들어가나요?

기본적으로 `iss`, `sub`, `iat`, `exp`와 함께 `email`, `name`, `provider`가 들어갑니다.  
헤더에는 `alg=RS256`, `kid`, `typ=JWT`가 들어갑니다.

### Q27. `kid`는 왜 중요한가요?

검증자가 JWK Set에서 어떤 공개 키를 써야 하는지 찾는 기준이기 때문입니다.  
토큰 헤더의 `kid`와 JWK Set의 `kid`가 맞아야 검증이 가능합니다.

### Q28. OIDC discovery endpoint는 왜 노출하나요?

외부 검증자가 issuer와 JWK URI를 자동으로 찾을 수 있게 하기 위해서입니다.  
현재 프로젝트는 `/.well-known/openid-configuration`과 `/.well-known/jwks.json`를 제공합니다.

### Q29. 왜 `jwks.json`에 private key가 안 보이나요?

정상입니다.  
JWK Set은 검증용 공개 키만 공개해야 하며, private key material은 노출되면 안 됩니다.

### Q30. 로컬 RSA와 Vault Transit의 차이는 뭐라고 설명하면 되나요?

- 로컬 RSA: 애플리케이션이 private key로 직접 서명합니다.
- Vault Transit: 애플리케이션은 public key를 읽고, 서명은 Vault API에 위임합니다.

즉 발급기 자체는 같아도 signer와 key source가 다릅니다.

### Q31. Vault를 켜면 어떤 점이 좋아지나요?

private key를 애플리케이션 내부에 고정하지 않아도 되고, 키 관리와 교체를 외부화할 수 있습니다.  
대신 Vault 네트워크와 운영 설정에 더 의존하게 됩니다.

### 6. 현재 없는 기능과 확장 포인트

### Q32. 이 프로젝트는 resource server인가요?

아직 아닙니다.  
현재는 JWT를 발급하고 discovery/JWK를 공개하지만, bearer token을 읽어 보호 API를 검증하는 `oauth2ResourceServer` 구성은 없습니다.

### Q33. 이 프로젝트는 authorization server인가요?

엄밀한 의미의 full OAuth2 Authorization Server는 아닙니다.  
하지만 자체 JWT 발급과 OIDC discovery를 제공하므로, 인증 서버의 일부 역할은 수행합니다.

### Q34. role 기반 권한 체크는 어디서 하나요?

현재는 하지 않습니다.  
`@PreAuthorize`, `@Secured`, `hasRole`, `hasAuthority` 사용이 없습니다.

### Q35. refresh token은 있나요?

없습니다.  
현재 토큰 모델과 응답 DTO는 access token만 다룹니다.

### Q36. 앞으로 보안을 확장한다면 어디부터 손보는 게 좋나요?

현재 구조상 우선순위는 아래 순서가 자연스럽습니다.

1. provider 추론을 naming convention이 아니라 명시적 매핑으로 바꾸기
2. role/authority 모델 도입
3. resource server 검증 경로 도입
4. refresh token 또는 세션 전략 재설계

## Result

이 질문집을 기준으로 하면 아래 질문에 답할 수 있어야 합니다.

- 현재 적용된 security와 미적용 security는 무엇인가?
- OAuth2 redirect, callback, `/complete`는 왜 그렇게 나뉘는가?
- `Authentication`, `principal`, `OidcUser`, `GrantedAuthority`는 어떤 관계인가?
- 비밀번호와 JWT 키는 각각 어디에서 보호되는가?
- 이 구조를 더 확장하려면 어떤 제약부터 풀어야 하는가?
