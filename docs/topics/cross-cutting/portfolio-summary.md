# Portfolio Summary

## 한 줄 소개

Clean Architecture 기반으로 인증/인가 서버를 설계하고, OAuth2 broker, Vault Transit, Flyway, GitHub Actions, GitOps 파이프라인을 연결한 프로젝트입니다.

## 핵심 포인트

- 레이어 경계를 `domain`, `application`, `presentation`, `infrastructure`, `bootstrap`으로 분리하고 ArchUnit 테스트로 구조 규칙을 검증
- Keycloak을 Identity Broker로 사용해 소셜 로그인 진입점을 단순화
- JWT 서명 책임을 Vault Transit으로 외부화하는 방향 채택
- `MigrationApplication`을 별도 진입점으로 두어 DB migration과 웹 실행 경로를 분리
- GitHub Actions에서 GHCR로 이미지를 발행하고 GitOps 저장소와 연계하는 배포 흐름 구성

## 이력서용 표현 예시

- Clean Architecture와 ArchUnit을 활용해 인증 서버의 레이어 경계를 테스트로 강제
- Keycloak broker 기반 OAuth2 로그인 구조를 설계해 외부 provider 의존 복잡도를 단순화
- Vault Transit과 Flyway, GitHub Actions, GitOps를 연결해 인증 서버의 운영 흐름을 정리

## 면접에서 강조할 포인트

- 단순 기능 구현보다 구조적 이유와 운영상 트레이드오프를 설명할 수 있다는 점
- 실제 경계 설정과 책임 분리를 코드, 테스트, 문서로 함께 관리했다는 점
