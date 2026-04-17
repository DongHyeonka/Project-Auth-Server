<!-- publish: velog -->
# Keycloak으로 인증 책임 넘기고 Spring Resource Server로 줄이기

> 로그인·회원가입·JWT 발급은 Keycloak이 맡고, 애플리케이션은 Bearer token 검증과 내부 사용자 식별만 맡도록 경계를 줄였다.

## 이 글을 쓰게 된 배경

처음에는 auth-server가 로컬 로그인, OAuth2 callback, JWT 발급, JWK 공개, Vault Transit 서명, 로컬 회원가입까지 직접 처리했다.
Keycloak을 OIDC Provider로 두기로 했다면 issuer와 token 발급, 그리고 회원가입 UX 책임을 애플리케이션에 계속 남길 이유가 약해진다.

## 문제 상황

인증 책임이 둘로 나뉘면 운영 질문이 애매해진다.

- 어떤 issuer의 token을 클라이언트가 써야 하는가?
- public key rotation은 어디서 관리하는가?
- 소셜 로그인 broker 설정은 애플리케이션 코드인가, IdP 설정인가?
- 회원가입을 두 곳에서 받으면 이중 쓰기 실패는 누가 복구하는가?
- Vault Transit 장애가 인증 장애인지 애플리케이션 장애인지 어떻게 나누는가?

## 처음에 했던 가정

처음에는 auth-server가 broker login 결과를 받아 자체 JWT를 발급하고, 로컬 signup도 유지하는 구조도 괜찮다고 봤다.
하지만 이 구조는 Keycloak을 도입해도 token 발급 서버와 회원 저장소를 하나 더 운영하는 셈이었고, signup → Keycloak 미러링의 이중 쓰기 문제까지 떠안게 됐다.

## 해결

Spring Security 설정을 OAuth2 Login에서 Resource Server로 바꿨다.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/realms/project-auth
```

회원가입 UX까지 Keycloak realm에 맡겼다. Keycloak `Realm Settings → Login → User registration: ON`으로 self-service 가입을 켜고, 소셜 로그인 broker도 Keycloak에 등록해 auth-server는 Google/GitHub Client Secret을 모르게 했다.

애플리케이션은 Keycloak token의 `sub`, `email`, `name`, `realm_access.roles`만 읽어 전용 principal로 바꾼다.
내부 사용자 row는 `provider=KEYCLOAK`, `provider_subject=sub` 기준으로 찾고, 없으면 새로 만든다. email 중복은 자동 연결하지 않고 409로 돌려준다.

자체 JWT 발급 어댑터, Vault Transit signer, 로컬 signup 컨트롤러와 `encoded_password` 컬럼까지 같이 지웠다.

## 무엇을 배웠나

- Resource Server는 token을 검증하는 곳이지 token을 발급하는 곳이 아니다.
- 실무에서는 email보다 IdP subject를 외부 사용자 고정 식별자로 삼는 편이 안전하다.
- Vault Transit은 자체 JWT 발급 책임을 유지할 때 의미가 있으며, Keycloak이 issuer가 되면 제거하는 편이 경계가 선명하다.
- 회원가입 UX를 자체 서버에 남기려 하면 Keycloak Admin API 이중 쓰기와 password 경로가 다시 복잡해진다. Keycloak-first가 결국 단순하다.

## 참고 자료

- 원문 문서: [`docs/topics/03-keycloak/02-adr-keycloak-resource-server.md`](../topics/03-keycloak/02-adr-keycloak-resource-server.md)
- 관련 코드: `ResourceServerSecurityConfiguration`, `KeycloakJwtAuthenticationConverter`, `KeycloakUserSynchronizer`
