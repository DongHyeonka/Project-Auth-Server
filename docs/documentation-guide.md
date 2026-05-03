# Documentation Guide

## 1. 목적

이 가이드는 이 저장소의 기술 문서를 일관된 방식으로 작성하기 위한 기준입니다. 핵심 목표는 문서를 읽는 사람이 다음 질문에 답할 수 있게 만드는 것입니다.

- 왜 이 결정을 했는가?
- 대안은 무엇이었는가?
- 실제로 어떻게 동작하는가?
- 운영과 유지보수에 어떤 영향이 있는가?

## 2. 핵심 원칙

### 2.1 기본 서술 순서

모든 기술 문서는 가능하면 다음 순서를 따릅니다.

1. `Why`: 어떤 문제를 해결하려는가
2. `What`: 구조와 핵심 컴포넌트가 무엇인가
3. `How`: 내부 동작과 적용 방법이 무엇인가
4. `Result`: 결과, 트레이드오프, 남은 리스크가 무엇인가

### 2.2 좋은 문서의 기준

- 사용법보다 의사결정 근거가 더 중요합니다.
- 대안 비교가 있어야 합니다.
- 실제 겪은 문제와 해결 과정이 있어야 합니다.
- 코드와 설명이 분리되지 않아야 합니다.
- 운영 관점의 영향이 있으면 반드시 적습니다.

## 3. 문서 유형별 기준

이 repo 는 **ADR** + **트러블슈팅 / 런북** 두 축만 다룹니다. 일반 학습 노트나 인터뷰 준비, postmortem 같은 형식은 두지 않습니다 (개인 프로젝트 규모에 맞춤).

| 유형 | 목적 | 권장 스타일 | 반드시 포함할 것 |
|------|------|-------------|------------------|
| ADR | 기술 선택 근거 기록 | ThoughtWorks Lightweight ADR | Context, Options, Decision, Consequences |
| Architecture Doc | 구조와 동작 설명 | Google Design Doc | Scope, Goals, Diagram, Alternatives |
| Runbook | 운영 절차 문서 | Docs-as-Code | Preconditions, Steps, Verification, Rollback |
| Troubleshooting | 문제 해결 기록 | 기술블로그 스타일 | Symptom, Cause, Fix, Lesson |

## 4. 폴더 구조 원칙

### 4.1 상위 구조

`docs/` 아래를 목적별로 나눕니다.

- `docs/architecture/`: 시스템 구조와 설계 의도
- `docs/topics/`: 토픽별 ADR + 트러블슈팅 / 런북
- `docs/development/`: 로컬 개발 환경 / 실험 자료
- `docs/standards/`: 코딩 가이드 (언어 / 스프링 / DB / 웹 / 테스트 표준)
- `docs/examples/`: 위 standards 의 적용 예시
- `docs/templates/`: 재사용 템플릿 (ADR / 런북 / 트러블슈팅)

같은 기술이 두 영역에 동시에 나타날 수 있습니다. 이때 기준은 *주제* 가 아니라 *문서 목적* 입니다.

- `docs/topics/03-keycloak/`: Keycloak 아키텍처 ADR + 트러블슈팅
- `docs/development/keycloak/`: 로컬 실행 방법, realm import 파일

### 4.2 토픽 폴더 구조

특정 기술을 깊이 정리할 때는 `docs/topics/` 아래에 번호형 폴더를 사용합니다.

```text
docs/topics/
├── 02-clean-architecture/
│   ├── README.md
│   ├── 02-error-handling.md
│   ├── 02a-validation-deep-dive.md
│   └── 03-adr-boundary-refactoring.md
├── 03-keycloak/
│   ├── README.md
│   ├── 01-architecture.md
│   ├── 02-adr-keycloak-resource-server.md
│   ├── 03-claim-role-design.md
│   └── 04-adr-token-ownership-cleanup.md
└── 04-logging/
    ├── README.md
    ├── 01-architecture.md
    └── 02-runbook-log-correlation-and-dev-actuator.md
```

### 4.3 파일명 규칙

- 순서를 표현하는 문서는 번호를 붙입니다. 예: `01-architecture.md`
- ADR 은 `<n>-adr-<topic>.md` 또는 `adr-why-<topic>.md` 형태를 사용합니다.
- Troubleshooting / Runbook 은 제목만 봐도 목적이 드러나야 합니다. 예: `<n>-runbook-log-correlation.md`
- 각 토픽 폴더에는 반드시 `README.md` 를 둡니다.
- 다이어그램 산출물은 `docs/architecture/diagrams/` 폴더에 모읍니다 (PNG / `.drawio` / `.excalidraw`).

## 5. 문서 작성 체크리스트

문서를 마무리하기 전에 아래 항목을 확인합니다.

- 이 문서가 해결하려는 질문이 첫 화면에 드러나는가?
- 선택 근거와 대안 비교가 있는가?
- 다이어그램이나 흐름 설명이 있는가?
- 실제 코드나 설정 예시가 있는가?
- 실제 문제 사례 또는 운영 관점이 포함되었는가?
- 결과와 남은 리스크가 정리되었는가?
- 관련 README 에 링크를 추가했는가?

## 6. 문서별 최소 섹션 가이드

### 6.1 ADR

```markdown
# [선택한 결정]
## Context — 어떤 문제 / 제약이 있었는가
## Options — 어떤 대안들이 있었는가
## Decision — 무엇을 골랐는가
## Consequences — 결과 / 트레이드오프 / 남은 리스크
```

### 6.2 아키텍처 문서

- Context & Scope
- Goals / Non-Goals
- Architecture Overview (다이어그램 필수)
- Detailed Design
- Alternatives Considered
- Cross-cutting Concerns
- Result / Enforcement

### 6.3 트러블슈팅 문서

- 한 줄 요약
- 배경
- 증상
- 원인 분석
- 해결
- 교훈

### 6.4 Runbook

- Preconditions (시작 전 상태)
- Steps (실제 명령)
- Verification (완료 확인)
- Rollback (실패 시 복구)

## 7. 이 저장소에서의 권장 적용 방식

- 시스템 전체 구조는 `docs/architecture/` 에 둡니다.
- 토픽 ADR + 트러블슈팅 / 런북은 `docs/topics/` 에 누적합니다.
- 로컬 실행, 배포 보조 절차는 `docs/development/` 에 Runbook 템플릿으로 작성합니다.
- 신규 문서를 추가할 때는 템플릿을 복사해 시작하고, 해당 토픽의 `README.md` 를 함께 갱신합니다.
- 인프라 운영 문서 (k8s / vault / argocd / cert-manager 등) 는 이 repo 가 아니라 [Project-Infra](https://github.com/donghyeon-ka/Project-Infra) 에 둡니다.

## 8. 가장 중요한 원칙

가장 가치 있는 문서는 *"삽질 기록"* 입니다. 깔끔한 이론 요약만 있는 문서보다, 실제 문제를 어떻게 관찰하고 가설을 세우고 원인을 찾고 해결했는지를 남긴 문서가 더 오래 쓰입니다.
