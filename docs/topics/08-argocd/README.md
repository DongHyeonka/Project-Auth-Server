# Argo CD

## 개요

이 주제는 Argo CD를 앱 저장소가 직접 포함하지 않더라도, 전체 배포 파이프라인에서 어떤 역할로 이해해야 하는지 정리합니다.

## 현재 프로젝트 맥락

- Argo CD 선언은 별도 GitOps 저장소에 있습니다.
- 이 저장소에서는 Argo CD를 직접 설정하기보다, 이미지 발행과 GitOps 이벤트 전달 지점만 담당합니다.

## 현재 문서

| 문서 | 내용 |
|------|------|
| [01-architecture.md](./01-architecture.md) | 현재 파이프라인에서 Argo CD의 위치와 책임 |

## 다음에 확장할 문서

- `02-app-of-apps.md`
- `03-sync-strategies.md`
- `04-troubleshooting.md`
