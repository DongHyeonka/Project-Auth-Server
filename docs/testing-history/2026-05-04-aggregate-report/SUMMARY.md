# 2026-05-04 멀티모듈 aggregate JaCoCo 리포트 도입

## 한 줄 결론

`./gradlew jacocoAggregateReport` 태스크를 도입하여, 모든 서브프로젝트의 jacoco execution data를 단일 리포트로 합쳤다. **bootstrap의 통합 테스트가 다른 모듈을 실행한 흔적까지 반영되어, presentation 모듈 라인 커버리지가 24.8% → 63.7% (aggregate 기준)으로 정직하게 드러났다.**

## 변경 범위

- `build.gradle` 루트에 `jacoco` 플러그인 적용
- `jacocoAggregateReport` 태스크 신규 등록 (모든 서브프로젝트 `test.exec` 합산)
- `archiveCoverageReport` 태스크가 aggregate 리포트도 자동 보관하도록 확장

## Aggregate 결과 (전체)

| Counter | covered/total | % |
|---|---|---|
| LINE | 684/880 | **77.7%** |
| BRANCH | 165/252 | 65.5% |
| METHOD | 183/214 | 85.5% |
| CLASS | 50/53 | 94.3% |

## 모듈별 단위 측정 vs Aggregate 비교 (라인)

| 패키지 | 모듈 단위 | Aggregate | 차이 (통합 테스트 효과) |
|---|---|---|---|
| `presentation/support/exception` | 24.8% | **63.7%** | +38.9%p |
| `presentation/support/response` | (포함됨) | **100.0%** | — |
| `application/support/logging` | 90.9% | 90.9% | (이미 jqwik으로 직접 커버) |
| `application/support/exception` | 71.9% | 71.9% | — |
| `config/web` (ApiErrorController 등) | 87.0% | 87.0% | — |
| `config/auth/security` | 85.7% | 85.7% | — |

**해석:** presentation 핸들러의 진짜 커버리지는 단위 측정이 보여주던 24.8%가 아니라 aggregate 기준 63.7%다. Tier 1 임계치(95%)까지의 격차도 이제 정확하게 보인다 — 31.3%p.

## 자동 검출 가능해진 회귀

- **presentation 핸들러의 진짜 커버리지가 운영 게이트에 노출됨** — 이전에는 단위 측정만 보여 통합 테스트 효과가 가려졌음
- 새 모듈 추가 시 aggregate 리포트가 자동으로 포함

## 측정 조건

| 항목 | 값 |
|---|---|
| BEFORE | 모듈별 jacocoTestReport만 존재 |
| AFTER | 추가로 `build/reports/jacoco/aggregate/html/index.html` 생성 |
| 측정 명령어 | `./gradlew clean jacocoAggregateReport` |
| 합산 대상 | 모든 서브프로젝트 `{module}/build/jacoco/test.exec` |
| Exclusion | `build.gradle`의 `jacocoExclusions` 그대로 적용 |

## 한계 / 미해결

- **63.7%는 여전히 Tier 1(95%) 미달** — 다음 사건(2번): presentation 핸들러 단위 테스트 또는 통합 테스트 추가로 95% 도달.
- **PIT는 aggregate 미지원** — PIT는 모듈별로만 측정 가능. 통합 테스트가 bootstrap에 있으면 presentation의 PIT mutation kill은 여전히 낮게 보임. 다음 사건(5번)에서 처리.

## 첨부

- AFTER 스냅샷: `coverage-history/v0.0.1-SNAPSHOT/2026-05-04-aggregate-after/`
  - 모듈별: `{module}/jacoco/html/index.html`
  - 통합: `aggregate/html/index.html`
