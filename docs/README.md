# Documentation Hub

이 저장소의 문서는 "읽는 사람이 의사결정을 할 수 있는가"를 기준으로 정리합니다.  
기본 서술 순서는 `Why -> What -> How -> Result`입니다.

## Source of Truth

- [documentation-guide.md](./documentation-guide.md): 문서 작성 기준과 스타일 규칙
- [topics/README.md](./topics/README.md): 기술별 심화 문서 폴더 구조 규칙
- [templates/README.md](./templates/README.md): 문서 템플릿 모음

## 현재 문서 영역

| 경로 | 목적 |
|------|------|
| `docs/architecture/` | 시스템 구조, 레이어링, 설계 의도 |
| `docs/development/` | 로컬 개발 환경, 실험, 설정 절차 |
| `docs/topics/` | 기술별 심화 문서와 학습 축적 |
| `docs/publish/` | Velog 등 외부 발행용 초안 |
| `docs/templates/` | ADR, RFC, Runbook 등 재사용 템플릿 |

운영 문서나 보안 문서가 늘어나면 `docs/operations/`, `docs/security/`처럼 영역을 분리합니다.

같은 기술이라도 역할이 다르면 위치를 분리합니다.

- `docs/topics/<topic>/`: 아키텍처, 선택 이유, 내부 동작, 트러블슈팅
- `docs/development/<topic>/`: 로컬 설정, 실행 절차, import 자산, 실험용 자료
- `docs/publish/`: 외부 공개용으로 재구성한 글 초안

## 작성 원칙

- 단순 사용법보다 "왜 이 선택을 했는가"를 먼저 설명합니다.
- 대안 비교와 트레이드오프를 남깁니다.
- 아키텍처 문서에는 Mermaid 다이어그램을 포함합니다.
- 문제 해결 문서에는 증상, 원인, 해결, 교훈을 남깁니다.
- 코드 블록은 "무엇을 썼는가"보다 "왜 이렇게 썼는가"와 함께 둡니다.

## 문서 추가 흐름

1. 문서 목적을 먼저 결정합니다. 예: ADR, RFC, Runbook, Troubleshooting
2. `docs/templates/`에서 가장 가까운 템플릿을 복사합니다.
3. 주제 문서라면 `docs/topics/` 규칙에 맞춰 폴더와 번호를 부여합니다.
4. 관련 `README.md`에 새 문서를 링크합니다.

## 빠른 링크

- [Architecture Overview](./architecture/README.md)
- [Development Docs](./development/)
- [Topic Docs](./topics/README.md)
- [Publish Drafts](./publish/README.md)
- [Cross-cutting Docs](./topics/cross-cutting/README.md)

## 📝 관련 블로그 글

Velog에 게시한 기술 글입니다. 삽질 과정과 학습 경험을 공유합니다.

| 주제 | 글 | 관련 문서 |
|------|-----|----------|
| <!-- Velog 글을 발행할 때마다 아래에 행을 추가합니다 --> | | |
