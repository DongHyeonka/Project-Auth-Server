# 2026-05-04 PIT mutation 게이트 활성화

## 한 줄 결론

application/presentation 모듈에 PIT mutation 임계치 게이트를 활성화. 두 모듈 모두 baseline 측정값 이하로 떨어지면 `./gradlew :module:pitest`가 빌드 실패한다. 게이트 활성 시점 측정값: **application 94% / threshold 90%**, **presentation 77% / threshold 75%**.

## 변경 범위

`build.gradle`:
- `:application:pitest`에 `mutationThreshold = 90`, `coverageThreshold = 90` 적용
- `:presentation:pitest`에 `mutationThreshold = 75`, `coverageThreshold = 90` 적용
- domain/bootstrap은 본 사건 범위 외 (domain은 wrapper만, bootstrap은 PIT 도입 후순위)

`docs/testing-coverage-policy.md`:
- "PIT는 baseline만" 문구를 게이트 활성 사실로 갱신
- 6개월 후 점진 상향 항목에서 "PIT 게이트 활성화" 제거 → "presentation 80~85%로 상향" 으로 교체

## 게이트 임계치 근거

| 모듈 | 측정값 | 임계치 | 마진 | 근거 |
|---|---|---|---|---|
| application | 94% (59/63 killed) | **90%** | +4%p | Tier 1 PIT 임계치(85~95%) 안전 통과. 테스트 추가/수정 시 마진 4%p 안에서 변동 허용. |
| presentation | 77% (58/75 killed) | **75%** | +2%p | 단위 테스트 추가로 32%→77%로 점프했지만 Tier 1 목표 85%까지는 미달. 살아남은 변이 17개 분석 후 후속 PR에서 상향. |

## Before / After

PIT 점수 자체는 application/presentation 양쪽 모두 직전 측정값과 동일 (Tier 1 단위 테스트 추가 사건에서 이미 점프). 본 사건은 그 측정값을 **빌드 게이트로 강제**한다는 변화.

| 모듈 | Mutation Coverage | Test Strength | Game Active? |
|---|---|---|---|
| application | 94% | 94% | ✅ 활성 (90% threshold) |
| presentation | 77% | 81% | ✅ 활성 (75% threshold) |

## 자동 검출 가능해진 회귀

- 누가 단언을 약화시켜 mutation kill이 임계치 아래로 떨어지는 PR → 빌드 실패
- 테스트를 통째로 삭제/skip하는 PR → 빌드 실패
- 새 코드를 추가하면서 mutation 대상이 늘어나 점수가 떨어지면 → 빌드 실패 (자연스러운 압력으로 작동)

## 측정 조건

| 항목 | 값 |
|---|---|
| 측정 명령어 | `./gradlew :application:pitest :presentation:pitest` |
| PIT 버전 | 1.19.1 |
| pitest-junit5-plugin | 1.2.2 |
| 게이트 활성 검증 | 두 명령어 모두 BUILD SUCCESSFUL |

## 한계 / 미해결

- **presentation 17개 mutation 살아남음** — `ValidationExceptionHandler`의 일부 분기/메시지 폴백이 아직 단언 약함. 다음 사건: 살아남은 변이 분석 + 단언 강화로 80~85%까지.
- **bootstrap PIT 미적용** — bootstrap은 Spring 통합 테스트가 무거워 PIT 실행 시간 큼. 별도 nightly CI에서 측정하는 정책 검토.
- **domain PIT 미적용** — wrapper 예외만 있어 mutation 생성 안 됨. 도메인 모델이 자라면 추가.

## 첨부

- AFTER 스냅샷: `coverage-history/v0.0.1-SNAPSHOT/2026-05-04-pit-gate-active/`
