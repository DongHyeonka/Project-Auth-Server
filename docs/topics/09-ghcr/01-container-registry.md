# GHCR 이미지 발행 구조

## Why

컨테이너 이미지는 애플리케이션 코드와 배포 환경을 연결하는 핵심 산출물이므로, 태그 전략과 발행 경로가 명확해야 합니다.

## What

현재 `.github/workflows/dev-ci-cd.yml`은 다음 순서로 동작합니다.

- 테스트 실행
- Docker Buildx 준비
- `ghcr.io/<owner>/project-auth-server` 이름 계산
- `dev`, `<short-sha>` 태그로 이미지 푸시

## Result

환경용 고정 태그와 추적 가능한 commit 태그를 함께 사용해 운영 추적성을 높입니다.
