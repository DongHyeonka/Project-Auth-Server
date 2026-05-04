# 2026-05-04 jacocoExclusions 정밀화

## 한 줄 결론

`**/config/**` 광역 제외를 **클래스명 컨벤션 기반**(`*Configuration`/`*Config`/`*Properties`/`*Application`)으로 좁혀, **bootstrap 모듈 라인 커버리지가 정상 운영 빌드 기준 0% → 86.8%로 노출**되도록 했다. 그동안 핵심 핸들러(ApiErrorController, SecurityResponseExceptionHandler 등)가 측정에서 통째로 빠져 있던 문제를 해소했다.

## 변경 범위

- `build.gradle`의 `jacocoExclusions` 패턴 변경
  - 제거: `**/AuthApplication.class`, `**/config/**`
  - 추가: `**/*Application.class`, `**/*Configuration.class`, `**/*Config.class`, `**/*Properties.class`
- `docs/testing-coverage-policy.md` 제외 목록 업데이트
- 코드 변경 없음, 테스트 변경 없음 (측정 정책만 수정)

## Before / After 수치 (운영 빌드 기준)

| 모듈 | BEFORE 라인 | AFTER 라인 | BEFORE 메서드 | AFTER 메서드 |
|---|---|---|---|---|
| `application` | 70.9% | 70.9% | 63.6% | 63.6% |
| `presentation` | 24.8% | 24.8% | 25.0% | 25.0% |
| `infrastructure` | 81.5% | 81.5% | 79.2% | 79.2% |
| `bootstrap` | **0.0%** | **86.8%** | **0.0%** | **95.2%** |

> bootstrap의 BEFORE 0%는 진짜 0%가 아니라 `**/config/**` exclusion 때문에 핸들러가 통째로 측정에서 빠진 결과였다. 사과-사과 비교는 이전 사건(2026-05-04 jqwik 도입)에서 80.7% → 80.3%로 확인되었으며, 본 사건은 그 진짜 점수가 운영 빌드에서도 보이게 만든 정책 정밀화다.

## 자동 검출 가능해진 회귀

- 핸들러를 직접 수정한 PR이 운영 게이트(`coverageGate`)에 잡히게 됨 — 이전에는 측정 자체가 안 되어 어떤 변경도 통과
- 핵심 핸들러가 신규 분기를 추가하면 라인/브랜치 커버리지가 즉시 떨어져 보임

## 측정 조건 (재현용)

| 항목 | 값 |
|---|---|
| BEFORE 커밋 | `53e27cf` (Tier 4 제거 직후) |
| AFTER 커밋 | (본 커밋) |
| 측정 명령어 | `./gradlew clean test jacocoTestReport` |
| 측정 조건 차이 | `jacocoExclusions` 정의만 변경, 그 외 모두 동일 |

## 한계 / 미해결

- **presentation 24.8%는 변하지 않음** — 통합 테스트가 bootstrap 모듈에 있어 presentation 모듈 단위 측정에 안 잡히는 구조적 문제. **다음 사건: aggregate 리포트 도입**으로 해결 예정.
- bootstrap 86.8%지만 **Tier 1 임계치(95%)에는 아직 미달** — `**/config/**`에 가려져 있던 핸들러 일부에 단위 테스트가 추가로 필요.

## 첨부

- AFTER 스냅샷: `coverage-history/v0.0.1-SNAPSHOT/2026-05-04-exclusion-precision-after/`
- 직전 BEFORE 스냅샷: `coverage-history/v0.0.1-SNAPSHOT/2026-05-04-jqwik-after/`
