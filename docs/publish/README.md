# Publish Drafts

`docs/publish/`는 Velog 같은 외부 채널에 바로 옮겨 적을 수 있는 공개용 초안을 보관하는 영역입니다.

## 이 폴더의 역할

- repo 원문 문서를 외부 독자 기준으로 다시 정리합니다.
- 민감한 정보와 내부 전용 맥락을 걷어냅니다.
- 게시 전에 제목, 서사, 교훈을 다듬은 버전을 남깁니다.

## 작성 원칙

- 원문은 `docs/topics/` 또는 `docs/development/`에 먼저 남깁니다.
- 공개용 초안은 [velog-post-template.md](../templates/velog-post-template.md)를 복사해 시작합니다.
- 원문과 공개용 초안은 서로 링크합니다.
- 공개용 초안에서는 secret, 내부 URL, 조직 전용 정보, 불필요한 파일 경로를 제거합니다.

## 권장 파일명

- `spring-security-securitycontext-lost.md`
- `keycloak-broker-flow.md`
- `flyway-migration-runner.md`

즉 "기술명 + 핵심 문제/주제"가 드러나는 이름을 권장합니다.

## 기본 흐름

1. 원문 기록: `docs/topics/.../troubleshooting` 또는 관련 문서
2. 공개용 초안 작성: `docs/publish/...`
3. Velog에 게시
4. 게시 후 원문 문서나 `docs/README.md`에 외부 링크 추가

## 현재 초안

| 문서 | 내용 |
|------|------|
| [keycloak-resource-server-transition.md](./keycloak-resource-server-transition.md) | Keycloak으로 인증 책임을 옮기고 auth-server를 Resource Server로 줄인 전환 기록 |
| [spring-security-why-oauth2-login-still-needs-session.md](./spring-security-why-oauth2-login-still-needs-session.md) | JWT 기반 로그인 결과와 OAuth2 세션 유지가 왜 동시에 필요한지 설명하는 글 초안 |
| [spring-security-keycloak-kc-idp-hint.md](./spring-security-keycloak-kc-idp-hint.md) | Keycloak broker 구조에서 `kc_idp_hint`가 어떤 역할을 하는지 설명하는 글 초안 |
