<!-- publish: velog -->

# JWT를 주는 OAuth2 로그인인데도 왜 세션이 필요할까

> 최종 응답이 JWT라고 해서 로그인 과정 전체가 stateless한 것은 아니었습니다. OAuth2 authorization code flow는 redirect와 callback 사이의 중간 상태를 유지해야 했고, 그 지점을 Spring Security 세션 모델이 맡고 있었습니다.

## 이 글을 쓰게 된 배경

- 인증 서버 프로젝트에서 OAuth2 로그인 성공 후 최종적으로는 JWT access token을 반환하는 구조를 만들고 있었습니다.
- 그런데 구현을 따라가다 보니 "우리는 JWT를 쓰는데 왜 callback 뒤에도 인증 상태가 남아 있지?"라는 질문이 계속 나왔습니다.
- 이 지점을 이해하지 못하면 `/oauth2/authorization/...`, `/login/oauth2/code/...`, `/api/v1/auth/oauth2/complete`가 왜 분리되어 있는지도 설명하기 어려웠습니다.

## 문제 상황

- 브라우저에서는 소셜 로그인 버튼을 누르면 정상적으로 외부 로그인 페이지로 이동합니다.
- callback도 성공한 것처럼 보이는데, 마지막 `/complete` 처리에서 인증 객체가 없으면 흐름이 끊길 수 있습니다.
- 겉으로 보면 "JWT 기반 인증 서버인데 세션에 의존하는 이상한 구조"처럼 보였습니다.

## 처음에 했던 가정

- JWT를 발급하는 서버라면 처음부터 끝까지 stateless해야 한다고 생각했습니다.
- 따라서 callback 이후 controller에 `Authentication`이 전달되는 구조가 어색해 보였습니다.
- 세션이 살아 있다는 사실 자체가 설계 실수처럼 느껴지기도 했습니다.

## 삽질 과정

### 시도 1

- 로컬 로그인 API와 OAuth2 로그인 API를 같은 관점으로 보려고 했습니다.
- 하지만 로컬 로그인은 단일 요청에서 끝나고, OAuth2 로그인은 redirect와 callback이 포함된 다단계 흐름이라 전제가 달랐습니다.

### 시도 2

- 최종 응답이 JWT라는 점에만 집중해 중간 상태가 필요 없다고 가정했습니다.
- 그런데 Spring Security는 authorization request와 callback 인증 상태를 연결하기 위해 세션을 사용하고 있었습니다.

### 시도 3

- `/api/v1/auth/oauth2/complete`를 공개 endpoint처럼 취급하면 되지 않을까 생각했습니다.
- 하지만 이 endpoint는 callback 이후 이미 인증된 사용자만 접근해야 하는 후처리 endpoint라 공개 경로로 풀면 구조가 흐려졌습니다.

## 원인

문제의 핵심은 "최종 인증 수단"과 "로그인 프로토콜의 중간 상태 관리 방식"을 같은 것으로 본 데 있었습니다.

OAuth2 authorization code flow는 아래 단계로 동작합니다.

1. 사용자를 authorization endpoint로 redirect한다.
2. 외부 provider 또는 broker가 인증을 수행한다.
3. callback으로 돌아온다.
4. callback 결과를 애플리케이션 후처리로 연결한다.

이 과정은 단일 HTTP 요청이 아닙니다.  
그래서 callback 이전과 이후를 이어 줄 상태가 필요하고, Spring Security OAuth2 client는 그 역할을 기본적으로 세션으로 처리합니다.

즉 결론은 간단합니다.

> JWT는 로그인 결과물이고, 세션은 로그인 프로토콜을 끝까지 이어 주는 임시 운반체였습니다.

## 해결

```text
1. OAuth2 로그인은 Spring Security oauth2Login()에 맡긴다.
2. callback 이후 success handler에서 애플리케이션 후처리 endpoint로 이동시킨다.
3. 후처리 endpoint는 공개하지 않고, callback 이후 인증 상태가 유지된 요청만 받는다.
4. 최종 응답으로 JWT를 반환하되, redirect/callback 단계의 세션 사용은 정상 동작으로 받아들인다.
```

프로젝트에서는 callback 이후 `/api/v1/auth/oauth2/complete`에서 `Authentication`을 받아 사용자 조회/등록과 JWT 발급을 마무리하도록 정리했습니다.

## 무엇을 배웠나

- JWT를 쓴다는 사실만으로 모든 인증 흐름이 stateless해지는 것은 아닙니다.
- OAuth2 authorization code flow는 redirect와 callback 사이의 연결 고리가 필요합니다.
- Spring Security를 이해할 때는 "최종 응답 형식"보다 "중간 프로토콜 상태를 누가 관리하는가"를 먼저 봐야 합니다.

## 글을 마치며

이 문제를 이해하고 나니 세 가지가 동시에 정리됐습니다.

- 왜 `/oauth2/authorization/...`가 따로 있는지
- 왜 callback 뒤에 `/complete`가 한 번 더 필요한지
- 왜 JWT 기반 서버에서도 세션이 잠깐 필요할 수 있는지

OAuth2 로그인 구조를 읽을 때는 "토큰이 뭐냐"보다 "중간 상태가 어디에 저장되느냐"를 먼저 보는 편이 훨씬 빠릅니다.

## 참고 자료

- 원문 문서: [docs/topics/01-spring-security/03-authentication-flow.md](../topics/01-spring-security/03-authentication-flow.md)
- 원문 문서: [docs/topics/01-spring-security/05-framework-internals.md](../topics/01-spring-security/05-framework-internals.md)
- 관련 코드: `OAuth2SecurityConfiguration`, `OAuth2LoginSuccessHandler`, `AuthOAuth2Controller`
- 공식 문서:
  - Spring Security OAuth2 Client

---
> 🔗 이 내용의 기술 선택 근거와 전체 문서는
> 이 저장소의 `docs/topics/01-spring-security/`에서 이어서 볼 수 있습니다.
