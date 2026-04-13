# GitOps 원칙

## Why

애플리케이션 코드 저장소와 운영 선언 저장소를 분리하지 않으면, 어떤 저장소가 실제 배포의 진실한 원본인지 흐려지기 쉽습니다.

## What

현재 구조는 다음처럼 역할을 나눕니다.

- 이 저장소: 애플리케이션 코드, 로컬 개발 환경
- GitOps 저장소: Kubernetes manifest, 환경별 오버레이, Argo CD 선언

## How

`dev-ci-cd.yml`은 이미지 푸시 후 GitOps 저장소에 `repository_dispatch` 이벤트를 보내 배포 흐름을 이어갑니다.

## Result

배포 선언 변경과 애플리케이션 코드 변경의 책임 경계를 분리할 수 있습니다.
