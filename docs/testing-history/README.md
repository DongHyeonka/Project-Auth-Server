# Testing History

이 폴더는 테스트 인프라 변경의 사건 단위 비교 기록이다. 정책(임계치, Tier 분류)은 [`../testing-coverage-policy.md`](../testing-coverage-policy.md)를 참조한다.

## 폴더 구조

```
docs/testing-history/
├── README.md                              # 본 문서
└── {YYYY-MM-DD}-{사건명}/                 # 사건 단위 폴더 (영구 보존, git)
    └── SUMMARY.md                         # 한 페이지 결론 (수치 + 의미 + 한계)
```

원본 JaCoCo/PIT 스냅샷은 다음 위치에 **버전·날짜 단위로 누적 보관**한다 (git ignore, 로컬 전용):

```
coverage-history/
└── v{project.version}/                   # 예: v0.0.1-SNAPSHOT
    └── {YYYY-MM-DD}-{사건명}/            # 같은 버전 안에서 누적
        ├── application/{jacoco,pitest}/
        ├── presentation/{jacoco,pitest}/
        ├── infrastructure/jacoco/
        ├── bootstrap/jacoco/
        └── summary.txt
```

> 같은 버전 안에서는 사건이 누적된다 (`v0.0.1-SNAPSHOT/2026-05-04-jqwik-before`, `v0.0.1-SNAPSHOT/2026-05-04-jqwik-after`, `v0.0.1-SNAPSHOT/2026-05-20-aggregate-after` ...). 릴리스 버전이 올라가면 자동으로 새 디렉토리에 적재되어 버전 단위 비교도 가능하다.

## 라벨 명명 규칙 (강제)

- 형식: `{YYYY-MM-DD}-{kebab-case 사건명}`
- 정규식: `^\d{4}-\d{2}-\d{2}-[a-z0-9][a-z0-9-]*$`
- 예: `2026-05-04-jqwik-before`, `2026-05-04-jqwik-after`, `2026-08-12-aggregate-before`
- `archiveCoverageReport` 태스크가 형식 검증 + 같은 라벨 중복 차단

## 새 사건 기록 절차 (체크리스트)

1. **BEFORE 스냅샷 측정**
   ```bash
   git checkout {pre-change-commit}
   ./gradlew clean test jacocoTestReport :application:pitest :presentation:pitest
   ./gradlew archiveCoverageReport -Plabel=YYYY-MM-DD-사건명-before
   ```
2. **변경 적용 후 AFTER 스냅샷 측정**
   ```bash
   git checkout {feature-branch}
   ./gradlew clean test jacocoTestReport :application:pitest :presentation:pitest
   ./gradlew archiveCoverageReport -Plabel=YYYY-MM-DD-사건명-after
   ```
3. `docs/testing-history/{YYYY-MM-DD}-{사건명}/SUMMARY.md` 작성 (아래 템플릿)
4. 커밋 — `coverage-history/`는 ignore되므로 SUMMARY.md만 git에 들어간다

> **중요**: 측정 조건은 BEFORE/AFTER가 같아야 한다. 같은 명령어, 같은 도구 버전, 같은 exclusion. 사과-사과 비교가 아니면 수치를 비교할 수 없다.

## 어떻게 보나

| 보고 싶은 것 | 어디로 |
|---|---|
| 한 페이지 결론과 변화량 | `docs/testing-history/{사건명}/SUMMARY.md` |
| 모듈별 라인/분기 % | `coverage-history/v{ver}/{label}/{module}/jacoco/html/index.html` |
| PIT 단언 강도 | `coverage-history/v{ver}/{label}/{module}/pitest/index.html` |
| 정책(임계치/Tier) | `../testing-coverage-policy.md` |

## SUMMARY.md 템플릿

```markdown
# YYYY-MM-DD 사건명

## 한 줄 결론
무엇을 바꿔서 어떤 수치가 어떻게 변했는지 한 문장.

## 변경 범위
- 의존성/도구
- 추가/수정된 테스트
- 영향 모듈

## Before / After 수치 (사과-사과)
| 모듈 | BEFORE | AFTER | 변화 |
| ... |

## 자동 검출 가능해진 회귀 시나리오
1. 구체적 시나리오 1
2. ...

## 측정 조건 (재현용)
- BEFORE 커밋 / AFTER 커밋
- 측정 명령어
- 도구 버전
- 같은 exclusion 여부

## PIT (단언 강도)
| 모듈 | Mutations | Killed | Test Strength |
| ... |

## 한계 / 미해결
- 다음 사건의 입력이 될 항목들

## 첨부 (원본 스냅샷)
- coverage-history/v{ver}/{YYYY-MM-DD}-사건명-before/
- coverage-history/v{ver}/{YYYY-MM-DD}-사건명-after/
```

## 보존 정책

- `docs/testing-history/{사건명}/SUMMARY.md` — **영구 보존** (git)
- `coverage-history/v{ver}/{label}/` 원본 스냅샷 — **로컬 전용**, 누적 보관, 필요 시 재생성 가능
- 진급/감사 등 외부 제출이 필요하면 그때 별도 zip으로 export
- 스크린샷은 보관하지 않는다 — HTML 리포트가 원본이다. 외부 제출용은 그때 생성 후 폐기.

## 명령어 치트시트

```bash
# 일반 측정 (게이트 없음, 리포트만)
./gradlew clean test jacocoTestReport

# PIT까지 측정 (도구 도입 사건일 때)
./gradlew :application:pitest :presentation:pitest

# 스냅샷 archive (버전·라벨 필수)
./gradlew archiveCoverageReport -Plabel=2026-05-04-jqwik-after

# 게이트 검증 (CI에서 PR 단위)
./gradlew coverageGate
```
