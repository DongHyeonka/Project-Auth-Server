# GHCR

## 개요

이 주제는 현재 저장소의 CI가 Docker 이미지를 어떻게 빌드하고 GHCR로 발행하는지를 정리합니다.

## 현재 프로젝트 맥락

- GitHub Actions 워크플로우가 `deploy/docker/Dockerfile`로 이미지를 빌드합니다.
- 태그는 `dev`와 짧은 commit SHA를 함께 사용합니다.
- 발행 후 GitOps 저장소로 후속 이벤트를 보냅니다.

## 현재 문서

| 문서 | 내용 |
|------|------|
| [01-container-registry.md](./01-container-registry.md) | GHCR 발행 구조와 태그 전략 요약 |

## 다음에 확장할 문서

- `02-ci-integration.md`
- `03-image-management.md`
