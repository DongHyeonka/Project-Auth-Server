# Testing History

이 폴더는 테스트 인프라 변경의 before/after 비교 기록이다. 정책(임계치, Tier 분류)은 [`../testing-coverage-policy.md`](../testing-coverage-policy.md)를 참조한다.

## 폴더 구조

```
docs/testing-history/
├── README.md                              # 본 문서
└── {YYYY-MM-DD}-{사건명}/                 # 사건 단위 폴더
    └── SUMMARY.md                         # 한 페이지 결론 (수치 + 의미 + 한계)
```

원본 JaCoCo/PIT 스냅샷은 다음 위치에 보관한다 (git ignore 대상, 로컬 전용):

```
coverage-history/
├── before/      # 비교 기준점
└── after/       # 변경 후
```

## 폴더 명명 규칙

- 형식: `{YYYY-MM-DD}-{kebab-case 사건명}`
- 예: `2026-05-04-jqwik-introduction`, `2026-08-12-aggregate-report`

## 새 사건 기록 절차 (체크리스트)

1. **BEFORE 스냅샷 측정**
   - 변경 직전 커밋으로 `git checkout {commit}` 후 `./gradlew clean test jacocoTestReport`
   - `coverage-history/before/`로 보관 (또는 `./gradlew archiveCoverageReport -Plabel=before`)
2. **변경 적용 후 AFTER 스냅샷 측정**
   - 같은 명령어, 같은 측정 조건(같은 exclusion, 같은 도구 버전)
   - `coverage-history/after/`로 보관
3. **PIT는 도구 도입 사건에서만 의미** — 이후 사건은 PIT before/after 둘 다 측정
4. `docs/testing-history/{날짜}-{사건명}/SUMMARY.md` 작성
   - 템플릿: 아래 "SUMMARY.md 템플릿" 참조
5. 커밋 — `coverage-history/`는 ignore되므로 SUMMARY.md만 git에 들어간다

## 어떻게 보나

| 보고 싶은 것 | 어디로 |
|---|---|
| 한 페이지 결론과 변화량 | `{사건명}/SUMMARY.md` |
| 모듈별 라인/분기 % | `coverage-history/{시점}/{module}/jacoco/html/index.html` |
| PIT 단언 강도 | `coverage-history/{시점}/{module}/pitest/index.html` |
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

## Before / After 수치
| 모듈 | BEFORE | AFTER | 변화 |
| ... |

## 자동 검출 가능해진 회귀 시나리오
1. 구체적 시나리오 1
2. ...

## 측정 조건 (재현용)
- 커밋, 명령어, 도구 버전

## 한계 / 미해결
- 다음 사건의 입력이 될 항목들

## 첨부
- coverage-history/before/, after/ 경로
```

## 보존 정책

- `SUMMARY.md`는 영구 보존 (git)
- `coverage-history/` 원본 스냅샷은 **로컬 전용**, 필요 시 재생성 가능
  - 진급/감사 등 외부 제출이 필요하면 그때 별도 zip으로 export
- 스크린샷은 보관하지 않는다 — HTML 리포트가 원본이다. 외부 제출용은 그때 생성 후 폐기.
