# Topic Docs

`docs/topics/`는 특정 기술을 깊게 정리하는 영역입니다.  
여기서는 "왜 이 기술을 이렇게 썼는가"와 "실제 프로젝트에서 어떤 문제를 겪었는가"를 함께 남깁니다.

## 현재 토픽 인덱스

| 주제 | 목적 |
|------|------|
| [01-spring-security](./01-spring-security/README.md) | 인증 필터 체인과 OAuth2 로그인 흐름 |
| [02-clean-architecture](./02-clean-architecture/README.md) | 레이어 분리와 조립 원칙 |
| [03-keycloak](./03-keycloak/README.md) | Identity Broker와 OIDC 연동 구조 |
| [04-k3s](./04-k3s/README.md) | 로컬/개발 클러스터 운영 관점 정리 |
| [05-vault](./05-vault/README.md) | Transit 기반 JWT 서명과 시크릿 운영 |
| [06-flyway](./06-flyway/README.md) | migration 전략과 전용 실행 경로 |
| [07-gitops](./07-gitops/README.md) | 앱 저장소와 GitOps 저장소의 역할 분리 |
| [08-argocd](./08-argocd/README.md) | GitOps 동기화와 배포 관점 |
| [09-ghcr](./09-ghcr/README.md) | 이미지 빌드와 레지스트리 배포 흐름 |
| [10-logging](./10-logging/README.md) | access log, audit log, structured logging 적용 구조 |
| [cross-cutting](./cross-cutting/README.md) | 여러 기술을 가로지르는 정리 |

## 폴더 운영 규칙

- 번호는 탐색 순서와 학습 순서를 함께 표현합니다.
- 각 토픽 폴더는 `README.md`를 시작점으로 사용합니다.
- 최소 한 개 이상의 시작 문서를 함께 둡니다.
- 심화 문서는 여기 두고, 로컬 설정 런북은 `docs/development/`에 둡니다.

## Keycloak 역할 분리

- [docs/topics/03-keycloak](./03-keycloak/README.md): Keycloak 아키텍처, broker 패턴, 프로젝트 적용 배경
- [docs/development/keycloak/LOCAL_SETUP.md](../development/keycloak/LOCAL_SETUP.md): 로컬 실행 절차
- [docs/development/keycloak/realm/project-auth-realm-local.json](../development/keycloak/realm/project-auth-realm-local.json): 로컬 import 자산
