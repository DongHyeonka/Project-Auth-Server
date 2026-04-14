# Documentation Guide

## 1. 목적

이 가이드는 이 저장소의 기술 문서를 일관된 방식으로 작성하기 위한 기준입니다.  
핵심 목표는 문서를 읽는 사람이 다음 질문에 답할 수 있게 만드는 것입니다.

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

| 유형 | 목적 | 권장 스타일 | 반드시 포함할 것 |
|------|------|-------------|------------------|
| ADR | 기술 선택 근거 기록 | ThoughtWorks Lightweight ADR | Context, Options, Decision, Consequences |
| Architecture Doc | 구조와 동작 설명 | Google Design Doc | Scope, Goals, Diagram, Alternatives |
| RFC | 변경 제안과 합의 | Design Proposal | Problem, Proposal, Validation, Rollout |
| Runbook | 운영 절차 문서 | Docs-as-Code | Preconditions, Steps, Verification, Rollback |
| Troubleshooting | 문제 해결 기록 | 기술블로그 스타일 | Symptom, Cause, Fix, Lesson |
| Tech Spec | 설계 명세 | Technical Spec | Requirements, Architecture, Data/API, Risks |
| Postmortem | 장애 회고 | Incident Review | Impact, Timeline, Root Cause, Actions |
| TIL | 짧은 학습 기록 | Learning Note | Context, Learned, Mistake, Takeaway |

## 4. 폴더 구조 원칙

### 4.1 상위 구조

현재 저장소에서는 `docs/` 아래를 목적별로 나눕니다.

- `docs/architecture/`: 시스템 구조와 설계 의도
- `docs/development/`: 개발/실험/로컬 환경 문서
- `docs/topics/`: 특정 기술에 대한 심화 정리
- `docs/publish/`: Velog 등 외부 발행용 초안
- `docs/templates/`: 재사용 템플릿

같은 기술이 두 영역에 동시에 나타날 수 있습니다. 이때 기준은 주제가 아니라 문서의 목적입니다.

- `docs/topics/03-keycloak/`: Keycloak 아키텍처, 브로커 패턴, 트러블슈팅
- `docs/development/keycloak/`: 로컬 실행 방법, realm import 파일, 개발 환경 절차
- `docs/publish/`: 외부 공개용으로 다시 쓴 글 초안과 게시 전 원고

### 4.2 기술별 심화 문서 구조

특정 기술을 깊이 정리할 때는 `docs/topics/` 아래에 번호형 폴더를 사용합니다.

```text
docs/topics/
├── 01-spring-security/
│   ├── README.md
│   ├── 01-architecture.md
│   ├── 02-flow.md
│   ├── 03-practice.md
│   ├── 04-troubleshooting.md
│   ├── 05-adr-why-spring-security.md
│   └── diagrams/
└── cross-cutting/
    ├── full-pipeline.md
    ├── interview-prep.md
    └── portfolio-summary.md
```

### 4.3 파일명 규칙

- 순서를 표현하는 문서는 번호를 붙입니다. 예: `01-architecture.md`
- ADR은 `adr-why-<topic>.md` 형태를 우선 사용합니다.
- Troubleshooting, Runbook, Postmortem은 제목만 봐도 목적이 드러나야 합니다.
- 각 주제 폴더에는 반드시 `README.md`를 둡니다.
- 다이어그램 산출물은 가능하면 `diagrams/` 폴더에 모읍니다.

## 5. 문서 작성 체크리스트

문서를 마무리하기 전에 아래 항목을 확인합니다.

- 이 문서가 해결하려는 질문이 첫 화면에 드러나는가?
- 선택 근거와 대안 비교가 있는가?
- 다이어그램이나 흐름 설명이 있는가?
- 실제 코드나 설정 예시가 있는가?
- 실제 문제 사례 또는 운영 관점이 포함되었는가?
- 결과와 남은 리스크가 정리되었는가?
- 관련 README에 링크를 추가했는가?

## 6. 문서별 최소 섹션 가이드

### 6.1 공통 기술 문서

```markdown
# [기술명]
## 1. Why
## 2. What
## 3. How
## 4. Practice
## 5. Troubleshooting
## 6. Result / Trade-offs
```

### 6.2 아키텍처 문서

- Context & Scope
- Goals / Non-Goals
- Architecture Overview
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

## 7. 이 저장소에서의 권장 적용 방식

- 시스템 전체 구조는 `docs/architecture/`에 둡니다.
- 특정 기술 심화 정리는 `docs/topics/`에 누적합니다.
- 로컬 실행, 배포 보조 절차, 운영 수기성 문서는 목적에 맞는 Runbook 템플릿으로 작성합니다.
- Velog에 바로 옮길 글 초안은 `docs/publish/`에서 별도로 관리합니다.
- 신규 문서를 추가할 때는 템플릿을 복사해 시작하고, 해당 주제의 `README.md`를 함께 갱신합니다.
- 같은 기술의 문서라도 "심화 설명"과 "실행 절차"는 서로 다른 폴더에 둡니다.

## 8. 가장 중요한 원칙

가장 가치 있는 문서는 "삽질 기록"입니다.  
깔끔한 이론 요약만 있는 문서보다, 실제 문제를 어떻게 관찰하고 가설을 세우고 원인을 찾고 해결했는지를 남긴 문서가 더 오래 쓰입니다.

## 9. 외부 발행 규칙

이 저장소의 문서를 Velog에 게시할 때는 아래 규칙을 따릅니다.

### 9.1 발행 대상 표시

Velog에 옮길 문서는 파일 첫 줄에 아래 주석을 남깁니다.

```markdown
<!-- publish: velog -->
```

이 표시가 있는 문서는 동일한 마크다운을 Velog에 복사해 게시하는 대상입니다.

### 9.2 발행 대상 문서 유형

| 유형 | Velog 적합도 | 이유 |
|------|-------------|------|
| Troubleshooting (삽질 기록) | ⭐⭐⭐⭐⭐ | 블로그 독자가 가장 공감하고 검색하는 글 |
| ADR (기술 선택 근거) | ⭐⭐⭐⭐ | 제목을 재포장하면 기술 선택 과정 공유 글이 됨 |
| cross-cutting 문서 | ⭐⭐⭐ | 전체 파이프라인, 아키텍처 개요 등 시리즈 발행 가능 |
| Architecture Doc | ⭐⭐⭐ | 다이어그램 중심의 구조 설명 글로 발행 가능 |
| Runbook | ⭐⭐ | 설치/운영 가이드는 블로그보다 GitHub이 적합 |

### 9.3 Velog 글 구조

Velog에 게시할 때는 아래 섹션을 기본으로 사용합니다.

```markdown
# [기술/주제] 제목 — 한 줄로 핵심을 드러내는 부제

## 🎯 한 줄 요약
> 이 글의 결론을 한두 문장으로 적습니다.

## 📌 배경
## 🔍 문제
## 🪓 삽질 과정
## ✅ 해결
## 💡 교훈
## 📎 참고 자료
```

실제 작성 시에는 [docs/templates/velog-post-template.md](./templates/velog-post-template.md)를 복사해 시작합니다.

### 9.4 작성 흐름

repo 안에서는 아래 순서로 관리합니다.

1. 원문 문서를 `docs/topics/` 또는 `docs/development/`에 먼저 작성합니다.
2. 외부 공개용 초안을 `docs/publish/`에 별도 파일로 작성합니다.
3. 공개용 초안은 원문보다 더 읽기 쉽게 재구성하되, 원인과 교훈은 왜곡하지 않습니다.
4. 민감한 설정값, 내부 경로, 조직 특화 정보는 공개용 초안에서 제거합니다.

### 9.5 GitHub ↔ Velog 상호 링크

**Velog 글 하단에 추가:**

```markdown
---
> 🔗 이 내용의 기술 선택 근거(ADR)와 전체 문서는
> [GitHub docs/](https://github.com/<owner>/<repo>/tree/main/docs)에서 확인할 수 있습니다.
```

**GitHub `docs/README.md` 하단에 추가:**

Velog 글을 발행할 때마다 `docs/README.md`의 "관련 블로그 글" 섹션에 링크를 추가합니다.
