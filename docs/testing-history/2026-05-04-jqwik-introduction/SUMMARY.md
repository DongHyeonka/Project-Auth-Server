# 2026-05-04 jqwik 속성 기반 테스트 도입

## 한 줄 결론

보안 핵심(LogSanitizer)에 속성 기반 테스트를 추가하여 **application 모듈 분기 커버리지를 14.5% → 78.9%로 +64.4%p 끌어올렸으며**, 매퍼/JSON Pointer 변환에 대한 회귀 안전망(presentation 라인 3.4% → 24.8%)을 동시에 확보했다. 같은 측정 조건(exclusion 없음, 같은 명령어, 같은 도구 버전)에서 사과-사과 비교한 결과다.

## 변경 범위

- 신규 jqwik 의존성 도입 (1.9.3) — JUnit Platform에 `includeEngines` 추가
- 신규 속성 테스트 4개 클래스, 총 30개 속성
  - `LogSanitizerPropertyTest` (11 속성, application 모듈)
  - `ApiErrorHttpStatusMapperPropertyTest` (5 속성, presentation 모듈)
  - `JsonPointerConversionPropertyTest` (8 속성, presentation 모듈)
  - `RequestBoundApiResultFactoryPropertyTest` (6 속성, bootstrap 모듈)
- JaCoCo + PIT 플러그인 도입, Tier 기반 임계치 정책 수립
- 영향 범위: application / presentation / bootstrap 모듈

## Before / After 수치 (사과-사과 측정)

### 라인 커버리지

| 모듈 | BEFORE | AFTER | 변화 |
|---|---|---|---|
| `application` | 42.3% | **70.9%** | **+28.6%p** |
| `presentation` | 3.4% | **24.8%** | **+21.4%p** |
| `infrastructure` | 81.5% | 81.5% | — |
| `bootstrap` | 80.7% | 80.3% | -0.4%p (오차) |

### 분기 커버리지

| 모듈 | BEFORE | AFTER | 변화 |
|---|---|---|---|
| `application` | 14.5% | **78.9%** | **+64.4%p** |
| `presentation` | 3.3% | **42.3%** | **+39.0%p** |
| `infrastructure` | (브랜치 없음) | (브랜치 없음) | — |
| `bootstrap` | 59.7% | 56.6% | -3.1%p |

### 메서드 / 클래스

| 모듈 | METHOD before → after | CLASS before → after |
|---|---|---|
| `application` | 41.8% → 63.6% | 75.0% → 81.2% |
| `presentation` | 5.9% → 25.0% | 18.2% → 40.0% |
| `infrastructure` | 79.2% → 79.2% | 100% → 100% |
| `bootstrap` | 82.9% → 83.8% | 85.7% → 85.2% |

### PIT (단언 강도 측정)

| 모듈 | Mutations | Killed | Test Strength |
|---|---|---|---|
| `application` | 64 | **46 (72%)** | 82% |
| `presentation` | 75 | 24 (32%) | **92%** (커버된 영역에서) |

> **PIT는 BEFORE에 측정되지 않았다.** PIT 플러그인 자체가 이 사건에서 처음 도입되었기 때문에 비교값은 없으며, AFTER의 절대 수치만 의미를 갖는다.

## 자동 검출 가능해진 회귀 시나리오

이전에는 단위 테스트가 0개였던 영역에 임의 입력 fuzz가 추가되어, 다음 회귀가 PR 단계에서 즉시 잡힌다:

1. **`LogSanitizer.clientIp` 마스킹 누락** — 임의의 0~255 옥텟 IPv4 주소 1만건에 대해 마지막 옥텟이 0으로 마스킹되지 않으면 즉시 실패.
2. **매퍼의 `AUTHENTICATION_REQUIRED → 401` 변경** — 정확값 단언 + PIT가 잡음.
3. **신규 `ClientFacingErrorCode` 추가 후 매퍼 분기 누락** — 매핑 테이블 가드 + classpath 가드 + 속성 테스트의 3중 안전망.
4. **JSON Pointer `~`/`/` 이스케이프 누락 (RFC 6901 위반)** — 임의 입력 fuzz로 즉시 실패.
5. **TraceIdFilter 누락 시 traceId가 JSON `null`로 직렬화되는 회귀** — sentinel `"-"` 강제.

## 측정 조건 (재현용)

| 항목 | 값 |
|---|---|
| BEFORE 커밋 | `c9bfdf2` (2026-05-03) |
| AFTER 커밋 | `6f62490` (2026-05-04) — measurement run에서 build.gradle의 `**/config/**` exclusion 임시 제거 |
| 측정 명령어 | `./gradlew clean test jacocoTestReport` |
| PIT 측정 명령어 | `./gradlew :application:pitest :presentation:pitest` |
| JaCoCo 버전 | 0.8.13 |
| PIT 버전 | 1.19.1 (junit5-plugin 1.2.2) |
| jqwik 버전 | 1.9.3 |

> **운영 빌드 차이점**: 운영 `build.gradle`에는 `**/config/**` exclusion이 적용되어 있어 일반 `./gradlew jacocoTestReport`는 bootstrap을 0%로 보고한다. 본 비교는 사과-사과를 위해 그 exclusion을 임시 제거한 측정값이다. exclusion 정밀화는 별도 follow-up.

## 한계 / 미해결

1. **presentation의 PIT mutation kill이 32%로 보이는 이유**는 통합 테스트가 bootstrap 모듈에 있어 PIT 측정에서 빠지기 때문이다. *커버된 영역에서의 단언 강도는 92%로 매우 높다*. 다음 사건: 멀티모듈 aggregate 리포트 도입.
2. **bootstrap의 운영 빌드 0%**는 측정 누락이지 코드 미커버가 아니다. 다음 사건: `**/config/**` exclusion을 핸들러를 포함하지 않도록 정밀화.
3. **Tier 1 임계치(95%+)에는 미도달.** 현재는 baseline 모드(게이트 비활성)이며, 점진적 상향 일정은 `docs/testing-coverage-policy.md`에 정의됨.

## 첨부 (원본 리포트)

- BEFORE 스냅샷: [`coverage-history/before/`](../../../coverage-history/before/)
- AFTER 스냅샷: [`coverage-history/after/`](../../../coverage-history/after/)

각 스냅샷의 모듈별 진입점:
```
{snapshot}/application/jacoco/html/index.html      # JaCoCo 라인/분기 표
{snapshot}/application/pitest/index.html           # PIT (AFTER 스냅샷에만 존재)
{snapshot}/presentation/jacoco/html/index.html
{snapshot}/presentation/pitest/index.html
{snapshot}/bootstrap/jacoco/html/index.html
{snapshot}/infrastructure/jacoco/html/index.html
```
