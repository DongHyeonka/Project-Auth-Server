# GitOps

## 개요

이 주제는 왜 이 저장소가 Kubernetes 선언을 직접 source of truth로 들고 있지 않고, 별도 GitOps 저장소와 역할을 분리하는지를 정리합니다.

## 현재 프로젝트 맥락

- 앱 저장소는 소스 코드와 로컬 개발 환경에 집중합니다.
- 운영 선언은 `Project-Auth-GitOps` 저장소에서 관리합니다.
- GitHub Actions가 이미지 발행 후 GitOps 저장소에 이벤트를 보냅니다.

## 현재 문서

| 문서 | 내용 |
|------|------|
| [01-principles.md](./01-principles.md) | 앱 저장소와 GitOps 저장소의 경계 정리 |

## 다음에 확장할 문서

- `02-workflow.md`
- `03-argocd-integration.md`
- `04-troubleshooting.md`
