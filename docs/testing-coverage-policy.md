# 테스트 커버리지 정책

이 문서는 본 프로젝트의 **JaCoCo / PIT / jqwik** 테스트 정책과 임계치 근거, 그리고 baseline 측정값을 기록한다. 빌드 스크립트(`build.gradle`)는 이 문서의 결정을 강제한다.

> 변경 시 build.gradle 주석과 함께 갱신할 것.

---

## 1. 도구 역할 분담

| 도구 | 측정하는 것 | 한계 |
|---|---|---|
| **JaCoCo** | 라인/브랜치가 실제로 실행됐는가 | 단언이 약하면 가짜 안심 |
| **PIT** (mutation) | 단언이 실제로 결함을 잡아내는가 | 무겁다 — 변경 영역만 돌리는 게 현실적 |
| **jqwik** (property-based) | 정의한 불변(property)이 임의 입력 1만건에 대해서도 성립하는가 | 적용 가능 영역이 제한적(순수 함수, 변환, 검증) |

**원칙**: JaCoCo만으로는 부족. PIT가 진짜 척도. jqwik은 **타깃 한정** 도구.

---

## 2. Tier 분류와 임계치

본 정책은 **3단계** Tier로 운영한다. 설정/main/생성 코드는 Tier로 분류하지 않고 jacocoExclusions로 통째 제외한다(아래 "Tier 분류 외" 섹션).

### Tier 1 — Critical (95~100%)
*깨지면 보안/데이터/계약 손상. 정책 표현물 자체.*

| 패키지/클래스 | JaCoCo Line | JaCoCo Branch | PIT Mutation |
|---|---|---|---|
| `domain.user.exception.*` | 100% | 100% | 95% |
| `application.support.exception.*` | 100% | 100% | 95% |
| `application.support.logging.LogSanitizer` | 100% | 100% | 95% |
| `presentation.support.exception.ApiErrorHttpStatusMapper` | 100% | 100% | 100% |
| `presentation.support.exception.*Handler` | 95% | 90% | 85% |
| `bootstrap.config.web.ApiErrorController` | 100% | 95% | 90% |
| `bootstrap.config.web.SecurityResponseExceptionHandler` | 100% | 95% | 90% |
| `bootstrap.config.auth.security.SecurityExceptionHandler` | 100% | 95% | 90% |

### Tier 2 — Core (80~90%)

| 패키지/클래스 | JaCoCo Line | JaCoCo Branch | PIT Mutation |
|---|---|---|---|
| `application.*.usecase`, `application.*.service` | 85~90% | 80~85% | 70~75% |
| `presentation.*.controller`, `presentation.*.dto` | 85~90% | 80~85% | 70~75% |
| `infrastructure.persistence.*.adapter` | 85% | 80% | 70% |
| `presentation.support.response.*` | 90% | 85% | 75% |

### Tier 3 — Supporting (60~75%)

| 패키지/클래스 | JaCoCo Line | JaCoCo Branch | PIT Mutation |
|---|---|---|---|
| `infrastructure.security.bcrypt.*`, `infrastructure.security.jwt.*` | 80% | 75% | 65% |
| `infrastructure.security.vault.*` | 75% | 70% | 60% |
| `application.support.audit.*` | 80% | 75% | 65% |
| `presentation.support.logging` (TraceIdFilter 등) | 85% | 80% | 70% |

### Tier 분류 외 (커버리지 게이트 대상 아님)

다음 영역은 Tier로 묶지 않고 `jacocoExclusions`에서 통째로 제외한다 — 통합 테스트가 컨텍스트 로딩 과정에서 자연스럽게 거치므로 별도 단위 테스트가 무가치한 영역이다.

| 패키지/클래스 | 처리 | 비고 |
|---|---|---|
| `*Configuration` 클래스 (`@Configuration` 컨벤션) | jacocoExclusions에서 제외 | 통합 테스트가 컨텍스트 로딩으로 자연 커버. 빈 와이어링만 있고 분기 로직 없음. |
| `*Config` 클래스 (짧은 변형, 예: `OpenApiConfig`) | jacocoExclusions에서 제외 | 위와 동일 |
| `*Properties` 클래스 (`@ConfigurationProperties`) | jacocoExclusions에서 제외 | setter/getter 보일러플레이트 |
| `*Application` 클래스 (Spring Boot main) | jacocoExclusions에서 제외 | 의미 없음 |
| Flyway migrations | (코드 아님) | SQL은 별도 마이그레이션 테스트 |

> **중요**: 이전 정책의 `**/config/**` 광역 제외는 폐기됨. 그 패턴은 핵심 핸들러(ApiErrorController, SecurityResponseExceptionHandler, RequestBoundApiResultFactory 등 — 모두 `bootstrap/config/web/`, `bootstrap/config/auth/security/`에 위치)까지 같이 빼버려서 운영 빌드에서 진짜 측정값이 보이지 않았다. 클래스명 컨벤션 기반 제외로 좁혀, 동작 코드는 모두 측정 대상이 된다.

---

## 3. 제외 목록 (build.gradle `jacocoExclusions`)

```
- **/*Application.class                       # Spring Boot main
- **/*Configuration.class                     # @Configuration 빈 와이어링
- **/*Config.class                            # @Configuration 짧은 변형 (OpenApiConfig 등)
- **/*Properties.class                        # @ConfigurationProperties 보일러플레이트
- **/dto/**/*Request.class, *Response.class   # boilerplate
- **/Q*.class                                 # QueryDSL generated
- **/*$Builder.class                          # Lombok generated
- **/generated-sources/**
```

> **TODO**: `**/config/**` exclusion이 너무 광범위하다. 핸들러(ApiErrorController, SecurityResponseExceptionHandler)는 게이트에서 별도 강제하지만, 이상적으로는 exclusion 패턴을 좁혀 핸들러를 normal coverage report에 포함시키는 게 맞다. 다음 PR에서 정밀화.

---

## 4. 게이트 정책

### 즉시 적용 (현재)
- **PR 게이트**: `./gradlew coverageGate` — 명시적 호출 시에만 실행. CI에서 PR마다 자동 호출.
- **임계치**: build.gradle의 `jacocoTestCoverageVerification`에 모듈별로 표현됨.
- **PIT는 baseline만**: `./gradlew mutationBaseline` — 점수만 기록, 게이트 활성화는 6개월 후 검토.

### 6개월 후 (점진 상향)
- Tier 2 PIT 임계치 +5%p
- Tier 3 라인 +5%p
- PIT mutation 점수 게이트(Tier 1: 85%+)

### 절대 금지
- 테스트 클래스에서 `@SuppressWarnings("...")`로 게이트 우회
- 단언 없는 테스트 (PIT가 잡지 못하는 경우 코드 리뷰에서 차단)

---

## 5. jqwik 적용 클래스 목록 (의무)

다음 클래스는 jqwik 속성 기반 테스트가 **반드시** 존재해야 한다. 신규 클래스 추가 시 코드 리뷰에서 확인.

| 클래스 | 테스트 파일 | 검증 속성 |
|---|---|---|
| `LogSanitizer` | `LogSanitizerPropertyTest` | 길이 한도, 제어문자 제거, IPv4 마스킹, 이메일 마스킹, hash prefix |
| `ApiErrorHttpStatusMapper` | `ApiErrorHttpStatusMapperPropertyTest` | 모든 enum이 4xx/5xx 매핑, 결정론, 누락 검출 |
| `ValidationExceptionHandler.fieldFieldToJsonPointer` | `JsonPointerConversionPropertyTest` | RFC 6901 형식, 이스케이프(`~`, `/`), dotted/indexed/nested 변환 |
| `RequestBoundApiResultFactory` | `RequestBoundApiResultFactoryPropertyTest` | traceId 폴백, sentinel 동작, 일관된 envelope |

권장 (다음 PR):
- 모든 토큰 파서/검증 로직
- 모든 변환(transform) 함수
- 모든 정규화(normalize) 함수

---

## 6. Baseline 측정 (2026-05-03)

### JaCoCo (모듈별 단위 + 통합 테스트 전체 실행 후)

| 모듈 | Line | Branch |
|---|---|---|
| `application` | 70.9% | 78.9% |
| `presentation` | 24.8% | 42.3% |
| `infrastructure` | 81.5% | — |
| `bootstrap` | 0.0% | — *(현 시점 `**/config/**` exclusion으로 핵심 클래스가 모두 제외됨; 다음 PR에서 정밀화)* |

> **해석**: presentation 모듈 단위 테스트가 핸들러 본문을 거의 안 거치는 이유는 통합 테스트가 bootstrap 모듈에 있기 때문. 멀티모듈 aggregate 리포트가 다음 단계 작업.

### PIT (현재 측정 가능 영역)

| 모듈 | Targets | Mutations | Killed | Test Strength |
|---|---|---|---|---|
| `application` | `support.exception.*`, `support.logging.*` | 64 | 46 (72%) | 82% |
| `presentation` | `support.exception.*`, `support.response.*` | 75 | 24 (32%) | **92%** *(coverage 자체가 24%)* |

> **해석**: presentation의 32% mutation kill은 *coverage가 낮기 때문*이지, 단언이 약해서가 아니다. coverage된 코드 안에서의 test strength는 92%로 매우 높다 → 단언이 의미가 있다는 증거. 통합 테스트가 PIT에 잡히도록 하는 것이 다음 단계.

---

## 7. 명령어 치트시트

```bash
# 전체 테스트 + JaCoCo 리포트 (게이트 없음 — 일반 빌드)
./gradlew test jacocoTestReport

# 게이트 검증 (CI/PR에서)
./gradlew coverageGate

# PIT baseline 측정 (수동/주간 CI)
./gradlew mutationBaseline

# 특정 모듈만
./gradlew :application:pitest
./gradlew :presentation:pitest

# JaCoCo 리포트 위치
{module}/build/reports/jacoco/test/html/index.html

# PIT 리포트 위치
{module}/build/reports/pitest/index.html
```

---

## 8. 다음 작업

1. **`**/config/**` exclusion 정밀화** — 핵심 핸들러 4개를 normal coverage report에 포함.
2. **멀티모듈 aggregate JaCoCo 리포트** — 통합 테스트가 다른 모듈 코드를 커버하는 정도를 정확히 측정.
3. **CI에서 `coverageGate` 자동 실행** — PR 단위 게이트 활성화.
4. **PIT mutation 점수 게이트 활성화 (6개월 후)** — Tier 1: 85%+, Tier 2: 70%+.
5. **MessageSource 도입 후** ClientFacingErrorCode i18n 전환 — 별도 메모리에 등록됨.
